package com.softwareverde.database.mysql.embedded.os;

import com.softwareverde.database.mysql.embedded.ProcessOutputLogger;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class UnixMysqlDatabase extends OperatingSystemSpecificMysqlDatabase {
    protected static final String CONFIGURATION_FILE_NAME = "mysql.conf";

    public UnixMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties) {
        super(databaseProperties);
    }

    @Override
    public void install() throws Exception {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final File dataDirectory = _databaseProperties.getDataDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

        // Always install the new binaries when invoked.
        _installFilesFromManifest();

        // If the data directory has already be initialized then exit.
        final Boolean mysqlDataWasAlreadyInstalled = _doesMysqlDataExist(dataDirectory);
        if (mysqlDataWasAlreadyInstalled) {
            _writeDataDirectoryHelper();
            _writeConfigFile(CONFIGURATION_FILE_NAME);
            return;
        }

        { // Ensure the data directory's path exists (but not the data directory itself).
            final File dataDirectoryParent = dataDirectory.getParentFile();
            if (dataDirectoryParent != null) {
                dataDirectoryParent.mkdirs();
            }
        }

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/init.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }
            command = (file.getPath() + " " + dataDirectory.getPath());
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + command);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (
                final InputStream inputStream = process.getInputStream();
                final OutputStream outputStream = process.getOutputStream()
            ) {
                outputStream.write(rootPassword.getBytes(StandardCharsets.UTF_8));
                outputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                final ProcessOutputLogger processOutputLogger = new ProcessOutputLogger(inputStream, Logger.getInstance(this.getClass()));
                processOutputLogger.start();

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
        _writeConfigFile(CONFIGURATION_FILE_NAME);
        _writeDataDirectoryVersion();

        nanoTimer.stop();
        Logger.debug("Database installed in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    @Override
    public void upgrade() throws Exception {
        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/upgrade.sh");
            if (! (file.isFile() && file.canExecute())) {
                Logger.warn("Unable to upgrade mysql tables, upgrade script not found.");
                return;
            }

            final Integer port = _databaseProperties.getPort();
            command = (file.getPath() + " " + port);
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + command);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (
                final InputStream inputStream = process.getInputStream();
                final OutputStream outputStream = process.getOutputStream()
            ) {
                outputStream.write(rootPassword.getBytes(StandardCharsets.UTF_8));
                outputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                final ProcessOutputLogger processOutputLogger = new ProcessOutputLogger(inputStream, Logger.getInstance(this.getClass()));
                processOutputLogger.start();

                final boolean upgradeWasSuccessful = process.waitFor(_upgradeTimeoutMs, TimeUnit.MILLISECONDS);
                if (! upgradeWasSuccessful) {
                    throw new RuntimeException("Unable to upgrade database. Upgrade failed after timeout.");
                }

                final boolean resultCodeWasSuccessful = (process.exitValue() == 0);
                if (! resultCodeWasSuccessful) {
                    throw new RuntimeException("Unable to upgrade database. Upgrade script failed.");
                }
            }
        }
        finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }

        _writeDataDirectoryVersion();
    }

    @Override
    public void start() throws Exception {
        if (! _shutdownHookInstalled) {
            _installShutdownHook();
        }

        _writeConfigFile(CONFIGURATION_FILE_NAME);

        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        final String command;
        {
            final File file = new File(installationDirectory.getPath() + "/run.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to start database. Run script not found.");
            }
            command = file.getPath();
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + command);
        _process = runtime.exec(command);

        _processOutputStream = _process.getOutputStream();
        _processInputStream = _process.getInputStream();
        _processInputReadThread = new ProcessOutputLogger(_processInputStream, Logger.getInstance(this.getClass()));
        _processInputReadThread.start();
    }
}
