package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public class EmbeddedMysqlDatabase extends MysqlDatabase {
    protected static File copyFile(final InputStream sourceStream, final String destinationFilename) {
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

    protected final String _configurationFileName = "mysql.conf";
    protected final EmbeddedDatabaseProperties _databaseProperties;
    protected final MysqlDatabaseConfiguration _databaseConfiguration;

    protected Process _process;

    protected void _writeConfigFile() {
        final File dataDirectory = _databaseProperties.getDataDirectory();

        dataDirectory.mkdirs();
        final String configFileLocation = (dataDirectory.getPath() + "/" + _configurationFileName);
        final String configFileContents = (_databaseConfiguration != null ? _databaseConfiguration.getDefaultsFile() : "");

        Logger.debug("Writing config file to: " + configFileLocation);
        IoUtil.putFileContents(configFileLocation, configFileContents.getBytes(StandardCharsets.UTF_8));
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties) {
        this(databaseProperties, null);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final MysqlDatabaseConfiguration databaseConfiguration) {
        super(databaseProperties);

        _databaseProperties = databaseProperties;
        _databaseConfiguration = databaseConfiguration;
    }

    public void install() throws Exception {
        final OperatingSystemType operatingSystemType = _databaseProperties.getOperatingSystemType();
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

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

            final String destination = (installationDirectory.getPath() + resource.substring(resourcePrefix.length() - 1));
            final InputStream inputStream = IoUtil.getResourceAsStream(resource);
            Logger.info("Copying: " + resource + " to " + destination);
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

        dataDirectory.mkdirs();
        _writeConfigFile();

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

                final boolean initWasSuccessful = process.waitFor(30, TimeUnit.SECONDS);
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
    }

    public void start() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();

        if (_databaseConfiguration != null) {
            _writeConfigFile();
        }

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/run.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }
            command = (file.getPath() + " " + dataDirectory.getPath());
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        _process = runtime.exec(command);

        final InputStream inputStream = _process.getInputStream();
        final BufferedReader processOutput = new BufferedReader(new InputStreamReader(inputStream));
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
    }

    public void stop() throws Exception {
        _process.destroy();

        final boolean initWasSuccessful = _process.waitFor(30, TimeUnit.SECONDS);
        if (! initWasSuccessful) {
            throw new RuntimeException("Unable to stop database. Shutdown failed after timeout.");
        }
    }
}
