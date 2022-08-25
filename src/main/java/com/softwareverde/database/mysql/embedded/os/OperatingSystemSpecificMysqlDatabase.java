package com.softwareverde.database.mysql.embedded.os;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.query.Query;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.Version;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public abstract class OperatingSystemSpecificMysqlDatabase {
    protected static File copyFile(final InputStream sourceStream, final String destinationFilename) {
        if (sourceStream == null) { return null; }

        try {
            final Path destinationPath = Paths.get(destinationFilename);
            final File file = new File(destinationFilename);
            file.mkdirs();

            Files.copy(sourceStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
        catch (final IOException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    protected final EmbeddedDatabaseProperties _databaseProperties;

    protected Boolean _shutdownHookInstalled = false;
    protected Long _timeoutMs = (30L * 1000L);
    protected Long _upgradeTimeoutMs = (60L * 1000L);

    protected Process _process;
    protected OutputStream _processOutputStream;
    protected InputStream _processInputStream;
    protected Thread _processInputReadThread;

    protected void _writeConfigFile(final String configurationFileName) {
        final File dataDirectory = _databaseProperties.getDataDirectory();

        dataDirectory.mkdirs();
        final String configFileLocation = (dataDirectory.getPath() + "/" + configurationFileName);
        final String configFileContents = _databaseProperties.getMysqlConfigurationFileContents();

        Logger.debug("Writing config file to: " + configFileLocation);
        IoUtil.putFileContents(configFileLocation, configFileContents.getBytes(StandardCharsets.UTF_8));
    }

    protected void _writeDataDirectoryVersion() {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final File installationVersionFile = new File(installationDirectory.getPath() + "/.version");
        final File dataVersionFile = new File(dataDirectory.getPath() + "/.version");

        final byte[] versionContents = IoUtil.getFileContents(installationVersionFile);
        IoUtil.putFileContents(dataVersionFile, versionContents);
    }

    protected Version _getPackagedVersion() {
        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        final String resourcePrefix = _getResourceDirectory(operatingSystemType);
        final String versionString = IoUtil.getResource(resourcePrefix + ".version");
        return Version.parse(versionString);
    }

    protected Boolean _doesMysqlDataExist() {
        final File dataDirectory = _databaseProperties.getDataDirectory();
        return _doesMysqlDataExist(dataDirectory);
    }

    protected Boolean _isInstalled() {
        final Version installedVersion = _getInstallationDirectoryVersion();
        final boolean mariaDbWasInstalled = (installedVersion != null);
        if (! mariaDbWasInstalled) { return false; }

        final Version packagedVersion = _getPackagedVersion();
        final boolean installedVersionIsLessThanPackagedVersion = (installedVersion.compareTo(packagedVersion) < 0);
        if (installedVersionIsLessThanPackagedVersion) { return false; }

        return _doesMysqlDataExist();
    }

    protected Version _getInstallationDirectoryVersion() {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File versionFile = new File(installationDirectory.getPath() + "/.version");
        final byte[] versionContentsBytes = IoUtil.getFileContents(versionFile);
        if (versionContentsBytes == null) { return null; }

        final String versionContents = StringUtil.bytesToString(versionContentsBytes);
        return Version.parse(versionContents);
    }

    protected Version _getDataDirectoryVersion() {
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final File versionFile = new File(dataDirectory.getPath() + "/.version");
        final byte[] versionContentsBytes = IoUtil.getFileContents(versionFile);
        if (versionContentsBytes == null) { return null; }

        final String versionContents = StringUtil.bytesToString(versionContentsBytes);
        return Version.parse(versionContents);
    }

    protected Boolean _doesMysqlDataExist(final File dataDirectory) {
        final File mysqlSystemDatabase = new File(dataDirectory.getPath() + "/mysql");
        return mysqlSystemDatabase.exists();
    }

    protected String _getResourceDirectory(final OperatingSystemType operatingSystemType) {
        return ("/mysql/" + operatingSystemType + "/");
    }

    protected void _installFilesFromManifest() {
        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        final String resourcePrefix = _getResourceDirectory(operatingSystemType);
        final String manifest = IoUtil.getResource(resourcePrefix + "manifest");
        if (Util.isBlank(manifest)) {
            throw new RuntimeException("Manifest not found for OS: " + operatingSystemType);
        }

        for (final String manifestEntry : manifest.split("\n")) {
            final String flags;
            final String resource;
            {
                final int spaceIndex = manifestEntry.lastIndexOf(' ');
                if (spaceIndex < 0) {
                    resource = manifestEntry;
                    flags = "";
                }
                else {
                    resource = manifestEntry.substring(0, spaceIndex);
                    flags = manifestEntry.substring(spaceIndex + 1);
                }
            }

            if (flags.contains("l")) {
                final String symbolicLinkDelimiter = " -> ";
                if (! manifestEntry.contains(symbolicLinkDelimiter)) {
                    throw new RuntimeException("Unable to copy resource: " + resource);
                }

                final Path link;
                final Path target;
                {
                    final String manifestEntryLink;
                    final String manifestEntryTarget;
                    {
                        final int delimiterIndex = resource.indexOf(symbolicLinkDelimiter);
                        manifestEntryLink = resource.substring(0, delimiterIndex);

                        final int targetIndex = (delimiterIndex + symbolicLinkDelimiter.length());
                        manifestEntryTarget = resource.substring(targetIndex);
                    }

                    final String linkDestinationString; // ex: "mysql/base/bin/mysqld"
                    {
                        final String installationDirectoryPath = installationDirectory.getPath();

                        final String linkResourcePath = manifestEntryLink.substring(resourcePrefix.length() - 1);
                        linkDestinationString = (installationDirectoryPath + linkResourcePath);
                    }

                    link = Paths.get(linkDestinationString);
                    target = Paths.get(manifestEntryTarget);
                }

                {
                    Logger.trace("Creating link: " + link + " to " + target);

                    final File linkFile = link.toFile();
                    if (linkFile.exists()) {
                        linkFile.delete();
                    }

                    try {
                        Files.createSymbolicLink(link, target);
                    }
                    catch (final IOException exception) {
                        throw new RuntimeException(exception);
                    }
                    catch (final UnsupportedOperationException exception) {
                        Logger.debug("Unable to create symbolic link: " + link); // Windows
                    }
                }

                continue;
            }

            final InputStream inputStream;
            {
                final InputStream wholeInputStream = IoUtil.getResourceAsStream(resource);
                if (wholeInputStream != null) {
                    inputStream = wholeInputStream;
                }
                else {
                    // Attempt to find and concatenate the resources as parts.
                    //  Resources may be fragmented in order to facilitate github hosting.
                    InputStream compositeStream = null;
                    int i = 0;
                    while (true) {
                        final InputStream fragmentStream = IoUtil.getResourceAsStream(resource + ".part" + i);
                        if (fragmentStream == null) { break; }

                        compositeStream = (compositeStream != null ? new SequenceInputStream(compositeStream, fragmentStream) : fragmentStream);

                        i += 1;
                    }
                    inputStream = compositeStream;
                }
            }

            final String destination;
            {
                final String installationDirectoryPath = installationDirectory.getPath();
                final String resourcePath = resource.substring(resourcePrefix.length() - 1);
                destination = (installationDirectoryPath + resourcePath);
            }
            Logger.trace("Extracting: " + resource + " to " + destination);
            final File copiedFile = OperatingSystemSpecificMysqlDatabase.copyFile(inputStream, destination);
            final boolean copyWasSuccessful = (copiedFile != null);
            if (! copyWasSuccessful) {
                throw new RuntimeException("Unable to copy resource: " + resource);
            }

            if (flags.contains("x")) {
                final boolean flagSetSuccessfully = copiedFile.setExecutable(true, true);
                if (! flagSetSuccessfully) {
                    throw new RuntimeException("Unable to set file flags: " + resource);
                }
            }
        }
    }

    /**
     * Stores the data directory location within the installation's `.datadir` file for run.sh/run.bat
     */
    protected void _writeDataDirectoryHelper() {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();

        final File dataDirectoryHelperFile = new File(installationDirectory.getPath() + "/.datadir");
        final Path installationDirectoryPath = Paths.get(installationDirectory.getAbsolutePath());
        final Path dataDirectoryPath = Paths.get(dataDirectory.getAbsolutePath());
        final Path relativeDataDirectoryPath = installationDirectoryPath.relativize(dataDirectoryPath);
        final String relativeDataDirectoryPathString = relativeDataDirectoryPath.toString();
        IoUtil.putFileContents(dataDirectoryHelperFile, relativeDataDirectoryPathString.getBytes(StandardCharsets.UTF_8));
    }

    protected void _installShutdownHook() {
        final Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    _stop();
                }
                catch (final Exception exception) {
                    final Process process = _process;
                    if (process != null) {
                        process.destroyForcibly();
                    }
                }
            }
        }));
    }

    protected void _stop() throws Exception {
        if (_process == null) { return; }
        Logger.info("Shutting down database.");

        try (
            final OutputStream outputStream = _processOutputStream;
            final InputStream inputStream = _processInputStream
        ) {
            try {
                final String exitString = ("exit" + System.lineSeparator());
                outputStream.write(exitString.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                // Wait for the input to be recognized and for the script to exit naturally.
                _processInputReadThread.join(_timeoutMs);
                Logger.trace("Process IO closed.");
            }
            catch (final Exception exception) {
                Logger.debug(exception);
            }
        }
        finally {
            _processOutputStream = null;
            _processInputStream = null;

            Logger.trace("Destroying process.");
            _process.destroy();

            try {
                final boolean initWasSuccessful = _process.waitFor(_timeoutMs, TimeUnit.MILLISECONDS);
                if (! initWasSuccessful) {
                    Logger.debug("Forcibly destroying process.");
                    _process.destroyForcibly();

                    throw new Exception("Unable to stop database. Shutdown failed after timeout.");
                }
            }
            finally {
                _process = null;
            }
        }
    }

    protected Boolean _isDatabaseOnline() {
        final DatabaseCredentials emptyRootDatabaseCredentials = new DatabaseCredentials("root", "");
        final DatabaseCredentials rootDatabaseCredentials = new DatabaseCredentials("root", _databaseProperties.getRootPassword());
        final DatabaseCredentials databaseCredentials = _databaseProperties.getCredentials();

        final String emptySchema = "";
        final String schema = _databaseProperties.getSchema();
        final String hostname = _databaseProperties.getHostname();
        final Integer port = _databaseProperties.getPort();
        final Properties connectionProperties = _databaseProperties.getConnectionProperties();

        final Query testQuery = new Query("SELECT 1");

        {// Attempt to connect via an empty root account (which is the default post-installation state of mysql)...
            final MysqlDatabaseConnectionFactory emptyRootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(hostname, port, emptySchema, emptyRootDatabaseCredentials.username, emptyRootDatabaseCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection databaseConnection = emptyRootDatabaseConnectionFactory.newConnection()) {
                databaseConnection.query(testQuery);
                return true;
            }
            catch (final DatabaseException exception) { }
        }

        { // Attempt to connect via root first since it should always have credentials (but may have been removed)...
            final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(hostname, port, emptySchema, rootDatabaseCredentials.username, rootDatabaseCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection databaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                databaseConnection.query(testQuery);
                return true;
            }
            catch (final DatabaseException exception) { }
        }

        { // If a root connect cannot be established, attempt to connect via user credentials...
            final MysqlDatabaseConnectionFactory databaseConnectionFactory = new MysqlDatabaseConnectionFactory(hostname, port, schema, databaseCredentials.username, databaseCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                databaseConnection.query(testQuery);
                return true;
            }
            catch (final DatabaseException exception) { }
        }

        return false;
    }

    public OperatingSystemSpecificMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties) {
        _databaseProperties = databaseProperties;
    }

    public Boolean isDatabaseOnline() {
        return _isDatabaseOnline();
    }

    public void waitForDatabaseToComeOnline(final Long timeoutMs) throws Exception {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        do {
            final Boolean databaseIsOnline = _isDatabaseOnline();
            if (databaseIsOnline) { return; }

            Thread.sleep(100L);

            nanoTimer.stop();
        } while (nanoTimer.getMillisecondsElapsed() < timeoutMs);

        if (nanoTimer.getMillisecondsElapsed() >= timeoutMs) {
            throw new DatabaseException("Server failed to come online after " + timeoutMs + "ms.");
        }
    }

    public void setTimeoutMs(final Long timeoutMs) {
        _timeoutMs = timeoutMs;
    }

    public void setUpgradeTimeout(final Long timeoutMs) {
        _upgradeTimeoutMs = timeoutMs;
    }

    public Long getTimeoutMs() {
        return _timeoutMs;
    }

    public Long getUpgradeTimeoutMs() {
        return _upgradeTimeoutMs;
    }

    public Boolean isInstalled() {
        return _isInstalled();
    }

    public Version getInstallationDirectoryVersion() {
        return _getInstallationDirectoryVersion();
    }

    public Version getDataDirectoryVersion() {
        return _getDataDirectoryVersion();
    }

    public void stop() throws Exception {
        _stop();
    }

    abstract public void install() throws Exception;
    abstract public void upgrade() throws Exception;
    abstract public void start() throws Exception;
}
