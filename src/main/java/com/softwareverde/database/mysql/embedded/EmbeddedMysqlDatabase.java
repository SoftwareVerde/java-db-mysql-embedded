package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.query.Query;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.SystemUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class EmbeddedMysqlDatabase extends MysqlDatabase {
    protected static final String UNIX_MYSQL_CONFIGURATION_FILE_NAME = "mysql.conf";
    protected static final String WINDOWS_MYSQL_CONFIGURATION_FILE_NAME = "my.ini";

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
    protected final DatabaseInitializer<Connection> _databaseInitializer;
    protected final MysqlDatabaseConfiguration _databaseConfiguration;

    protected Boolean _shutdownHookInstalled = false;
    protected Long _timeoutMs = (30L * 1000L);
    protected Process _process;
    protected OutputStream _processOutputStream;
    protected InputStream _processInputStream;
    protected Thread _processInputReadThread;

    protected void _deleteTestDatabase(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
    }

    protected void _initializeRootAccount(final MysqlDatabaseConnection databaseConnection, final DatabaseCredentials rootCredentials) throws DatabaseException {
        final String rootHost = "localhost"; // "127.0.0.1";
        final String systemAccount = "mariadb.sys";

        { // Restrict root to localhost and set root password...
            databaseConnection.executeSql(
                new Query("DELETE FROM mysql.user WHERE (user != ? AND user != ?) OR host != ?")
                    .setParameter(rootCredentials.username)
                    .setParameter(systemAccount)
                    .setParameter(rootHost)
            );

            databaseConnection.executeSql(new Query("FLUSH PRIVILEGES")); // Oddly necessary...

            databaseConnection.executeSql(
                new Query("ALTER USER ?@? IDENTIFIED BY ?")
                    .setParameter(rootCredentials.username)
                    .setParameter(rootHost)
                    .setParameter(rootCredentials.password)
            );

            databaseConnection.executeSql(new Query("FLUSH PRIVILEGES"));
        }
    }

    /**
     * Attempts to obtain the stored database version via the root connection and uses the user credential as failover.
     *  Returns 0 if the database has not been setup or if connections could not be established.
     */
    protected Integer _getDatabaseVersionNumber() {
        final DatabaseCredentials rootDatabaseCredentials = new DatabaseCredentials("root", _databaseProperties.getRootPassword());
        final DatabaseCredentials databaseCredentials = _databaseProperties.getCredentials();

        final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(_databaseProperties.getHostname(), _port, _databaseProperties.getSchema(), rootDatabaseCredentials.username, rootDatabaseCredentials.password, _connectionProperties);
        final MysqlDatabaseConnectionFactory databaseConnectionFactory = new MysqlDatabaseConnectionFactory(_databaseProperties.getHostname(), _port, _databaseProperties.getSchema(), databaseCredentials.username, databaseCredentials.password, _connectionProperties);

        Integer databaseVersionNumber = 0;

        try {
            // Attempt to connect via root first since it should always have credentials (but may have been removed)...
            try (final MysqlDatabaseConnection databaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                databaseVersionNumber = _databaseInitializer.getDatabaseVersionNumber(databaseConnection);
            }
        }
        catch (final DatabaseException ignoredException) { }

        if (databaseVersionNumber == 0) {
            // If a root connect cannot be established, attempt to connect via user credentials...
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                databaseVersionNumber = _databaseInitializer.getDatabaseVersionNumber(databaseConnection);
            }
            catch (final DatabaseException exception) { }
        }

        return databaseVersionNumber;
    }

    protected void _initializeDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final Properties connectionProperties) throws DatabaseException {
        final DatabaseCredentials rootCredentials = new DatabaseCredentials("root", databaseProperties.getRootPassword());

        final Integer databaseVersionNumber = _getDatabaseVersionNumber();
        if (databaseVersionNumber == 0) {
            Logger.info("Initializing database.");
            // If the database version wasn't able to be obtained initially then assume the database needs to be setup for its first run.
            final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties.getHostname(), databaseProperties.getPort(), "", rootCredentials.username, rootCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                _initializeRootAccount(rootDatabaseConnection, rootCredentials);
                _deleteTestDatabase(rootDatabaseConnection);
                databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
            }
        }

        final DatabaseCredentials maintenanceCredentials = databaseInitializer.getMaintenanceCredentials(databaseProperties);
        if (maintenanceCredentials != null) {
            // Switch over to the maintenance account for the database initialization...
            final MysqlDatabaseConnectionFactory maintenanceCredentialsDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties, maintenanceCredentials, connectionProperties);
            try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceCredentialsDatabaseConnectionFactory.newConnection()) {
                databaseInitializer.initializeDatabase(maintenanceDatabaseConnection);
                final Integer postUpgradeDatabaseVersionNumber = databaseInitializer.getDatabaseVersionNumber(maintenanceDatabaseConnection);
                if (postUpgradeDatabaseVersionNumber < 1) {
                    throw new DatabaseException("Database version must be set after first initialization.");
                }
            }
            catch (final DatabaseException databaseException) {
                throw new DatabaseException("Unable to complete database maintenance.", databaseException);
            }
        }
        else {
            Logger.info("Maintenance credentials are not available; database upgrades are not available.");
        }
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

                    throw new RuntimeException("Unable to stop database. Shutdown failed after timeout.");
                }
            }
            finally {
                _process = null;
            }
        }
    }

    protected Boolean _doesMysqlDataExist(final File dataDirectory) {
        final File mysqlSystemDatabase = new File(dataDirectory.getPath() + "/mysql");
        return mysqlSystemDatabase.exists();
    }

    protected void _writeConfigFile(final String configurationFileName) {
        final File dataDirectory = _databaseProperties.getDataDirectory();

        dataDirectory.mkdirs();
        final String configFileLocation = (dataDirectory.getPath() + "/" + configurationFileName);
        final String configFileContents = (_databaseConfiguration != null ? _databaseConfiguration.getDefaultsFile() : "");

        Logger.debug("Writing config file to: " + configFileLocation);
        IoUtil.putFileContents(configFileLocation, configFileContents.getBytes(StandardCharsets.UTF_8));
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

    protected void _installFilesFromManifest() {
        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        final String resourcePrefix = "/mysql/" + operatingSystemType + "/";
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

            final String destination = (installationDirectory.getPath() + resource.substring(resourcePrefix.length() - 1));
            Logger.debug("Extracting: " + resource + " to " + destination);
            final File copiedFile = EmbeddedMysqlDatabase.copyFile(inputStream, destination);
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

    protected void _installUnix() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

        // Always install the new binaries when invoked.
        _installFilesFromManifest();

        // If the data directory has already be initialized then exit.
        final Boolean mysqlDataWasAlreadyInstalled = _doesMysqlDataExist(dataDirectory);
        if (mysqlDataWasAlreadyInstalled) {
            _writeDataDirectoryHelper();
            _writeConfigFile(UNIX_MYSQL_CONFIGURATION_FILE_NAME);
            return;
        }

        final File dataDirectoryParent = dataDirectory.getParentFile();
        dataDirectoryParent.mkdirs(); // Ensure the data directory's path exists (but not the data directory itself).

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/init.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }
            command = (file.getPath() + " " + dataDirectory.getPath());
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (
                final InputStream inputStream = process.getInputStream();
                final OutputStream outputStream = process.getOutputStream();
                final BufferedReader processOutput = new BufferedReader(new InputStreamReader(inputStream))
            ) {
                outputStream.write(rootPassword.getBytes(StandardCharsets.UTF_8));
                outputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String line;
                            while ((line = processOutput.readLine()) != null) {
                                Logger.debug(line);
                            }
                        }
                        catch (final Exception exception) { }
                    }
                })).start();

                final boolean initWasSuccessful = process.waitFor(_timeoutMs, TimeUnit.MILLISECONDS);
                if (! initWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init failed after timeout.");
                }

                final boolean resultCodeWasSuccessful = (process.exitValue() == 0);
                if (! resultCodeWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init script failed.");
                }
            }
        }
        finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }

        _writeDataDirectoryHelper();
        _writeConfigFile(UNIX_MYSQL_CONFIGURATION_FILE_NAME);
    }

    protected void _installWindows() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

        // Always install the new binaries when invoked.
        _installFilesFromManifest();

        // If the data directory has already be initialized then exit.
        final Boolean mysqlDataWasAlreadyInstalled = _doesMysqlDataExist(dataDirectory);
        if (mysqlDataWasAlreadyInstalled) {
            _writeDataDirectoryHelper();
            _writeConfigFile(WINDOWS_MYSQL_CONFIGURATION_FILE_NAME);
            return;
        }

        final File dataDirectoryParent = dataDirectory.getParentFile();
        dataDirectoryParent.mkdirs(); // Ensure the data directory's path exists (but not the data directory itself).

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/base/bin/mysql_install_db.exe");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }

            // NOTE: mysql_install_db.exe detects the base directory on its own, and providing it is an error ("unknown variable").
            //  mysql_install_db.exe requires that the data directory is empty.
            //  mysql_install_db.exe generates a minimal my.ini file within the data directory, setting the basedir.
            command = (file.getPath() + " --datadir=" + dataDirectory.getPath() + " --password=" + rootPassword);
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (
                final InputStream inputStream = process.getInputStream();
                final BufferedReader processOutput = new BufferedReader(new InputStreamReader(inputStream))
            ) {
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String line;
                            while ((line = processOutput.readLine()) != null) {
                                Logger.debug(line);
                            }
                        }
                        catch (final Exception exception) { }
                    }
                })).start();

                final boolean initWasSuccessful = process.waitFor(_timeoutMs, TimeUnit.MILLISECONDS);
                if (! initWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init failed after timeout.");
                }

                final boolean resultCodeWasSuccessful = (process.exitValue() == 0);
                if (! resultCodeWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init script failed.");
                }
            }
        }
        finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }

        _writeDataDirectoryHelper();

        // NOTE: Since this command will create the data directory if it does not exist, and since the windows
        //  version of the mysql data installer requires the data directory not exist, this command must run after
        //  the data installation completes.
        _writeConfigFile(WINDOWS_MYSQL_CONFIGURATION_FILE_NAME);
    }

    protected void _startDatabaseUnix() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        if (_databaseConfiguration != null) {
            _writeConfigFile(UNIX_MYSQL_CONFIGURATION_FILE_NAME);
        }

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/run.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to start database. Run script not found.");
            }
            command = file.getPath();
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        _process = runtime.exec(command);

        _processOutputStream = _process.getOutputStream();
        _processInputStream = _process.getInputStream();
        _processInputReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (
                    final InputStream inputStream = _processInputStream;
                    final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    final BufferedReader processOutput = new BufferedReader(inputStreamReader)
                ) {
                    String line;
                    while ((line = processOutput.readLine()) != null) {
                        Logger.debug(line);
                    }
                }
                catch (final Exception exception) { }
                finally {
                    Logger.debug("Run.sh reader exiting.");
                }
            }
        });
        _processInputReadThread.start();
    }

    protected void _startDatabaseWindows() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        if (_databaseConfiguration != null) {
            _writeConfigFile(WINDOWS_MYSQL_CONFIGURATION_FILE_NAME);
        }

        final Long javaPid = SystemUtil.getProcessId();

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/run.bat");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to start database. Run script not found.");
            }
            command = (file.getPath() + " " + javaPid);
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        _process = runtime.exec(command);

        _processOutputStream = _process.getOutputStream();
        _processInputStream = _process.getInputStream();
        _processInputReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (
                    final InputStream inputStream = _processInputStream;
                    final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    final BufferedReader processOutput = new BufferedReader(inputStreamReader)
                ) {
                    String line;
                    while ((line = processOutput.readLine()) != null) {
                        Logger.debug(line);
                    }
                }
                catch (final Exception exception) { }
                finally {
                    Logger.debug("Run.bat reader exiting.");
                }
            }
        });
        _processInputReadThread.start();
    }

    protected Boolean _isDatabaseOnline() {
        final DatabaseCredentials rootDatabaseCredentials = new DatabaseCredentials("root", _databaseProperties.getRootPassword());
        final DatabaseCredentials databaseCredentials = _databaseProperties.getCredentials();

        final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(_databaseProperties.getHostname(), _port, "", rootDatabaseCredentials.username, rootDatabaseCredentials.password, _connectionProperties);
        final MysqlDatabaseConnectionFactory databaseConnectionFactory = new MysqlDatabaseConnectionFactory(_databaseProperties.getHostname(), _port, _databaseProperties.getSchema(), databaseCredentials.username, databaseCredentials.password, _connectionProperties);

        try {
            // Attempt to connect via root first since it should always have credentials (but may have been removed)...
            try (final MysqlDatabaseConnection databaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                databaseConnection.query(new Query("SELECT 1"));
                return true;
            }
        }
        catch (final DatabaseException ignoredException) {
            // If a root connect cannot be established, attempt to connect via user credentials...
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                databaseConnection.query(new Query("SELECT 1"));
                return true;
            }
            catch (final DatabaseException exception) {
                return false;
            }
        }
    }

    protected void _waitForDatabaseToComeOnline(final Long timeoutMs) throws Exception {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        do {
            final Boolean databaseIsOnline = _isDatabaseOnline();
            if (databaseIsOnline) { return; }

            Thread.sleep(250L);
            nanoTimer.stop();
        } while (nanoTimer.getMillisecondsElapsed() < timeoutMs);

        if (nanoTimer.getMillisecondsElapsed() >= timeoutMs) {
            throw new Exception("Server failed to come online after " + timeoutMs + "ms.");
        }
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer) {
        this(databaseProperties, databaseInitializer, null);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final MysqlDatabaseConfiguration databaseConfiguration) {
        this(databaseProperties, databaseInitializer, databaseConfiguration, null);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final MysqlDatabaseConfiguration databaseConfiguration, final Properties connectionProperties) {
        super(databaseProperties.getHostname(), databaseProperties.getPort(), databaseProperties.getCredentials().username, databaseProperties.getCredentials().password, connectionProperties);

        _schema = databaseProperties.getSchema();
        _databaseProperties = databaseProperties;
        _databaseConfiguration = (databaseConfiguration != null ? new MysqlDatabaseConfiguration(databaseConfiguration) : new MysqlDatabaseConfiguration());
        _databaseInitializer = databaseInitializer;

        _databaseConfiguration.setPort(_port);
    }

    protected void setTimeout(final Long timeoutMs) {
        _timeoutMs = timeoutMs;
    }

    public void install() throws Exception {
        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            _installWindows();
            return;
        }

        _installUnix();
    }

    public void start() throws Exception {
        if (! _shutdownHookInstalled) {
            _installShutdownHook();
        }

        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        if (operatingSystemType == OperatingSystemType.WINDOWS) {
            _startDatabaseWindows();
        }
        else {
            _startDatabaseUnix();
        }

        _waitForDatabaseToComeOnline(_timeoutMs);

        _initializeDatabase(_databaseProperties, _databaseInitializer, _connectionProperties);
    }

    public void stop() throws Exception {
        _stop();
    }

    public Boolean wasInstalled() {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();

        final File mariaDbBaseDirectory = new File(installationDirectory + "/base");
        final boolean mariaDbWasInstalled = mariaDbBaseDirectory.exists();
        if (! mariaDbWasInstalled) { return false; }

        return _doesMysqlDataExist(dataDirectory);
    }
}
