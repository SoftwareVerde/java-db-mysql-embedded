package com.softwareverde.database.mysql.embedded.os;

import com.softwareverde.database.mysql.embedded.ProcessOutputLogger;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.SystemUtil;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class WindowsMysqlDatabase extends OperatingSystemSpecificMysqlDatabase {
    protected static final String CONFIGURATION_FILE_NAME = "my.ini";

    public WindowsMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties) {
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

        final String[] command = new String[3];
        {
            final File file = new File(installationDirectory.getPath() + "/base/bin/mysql_install_db.exe");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }

            // NOTE: mysql_install_db.exe detects the base directory on its own, and providing it is an error ("unknown variable").
            //  mysql_install_db.exe requires that the data directory is empty.
            //  mysql_install_db.exe generates a minimal my.ini file within the data directory, setting the basedir.
            command[0] = file.getPath();
            command[1] = "--datadir=" + dataDirectory.getPath();
            command[2] = "--password=" + rootPassword;
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + String.join(" ", command));
        Process process = null;
        try {
            process = runtime.exec(command);

            try (final InputStream inputStream = process.getInputStream()) {
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
        // NOTE: Since this command will create the data directory if it does not exist, and since the windows
        //  version of the mysql data installer requires the data directory not exist, this command must run after
        //  the data installation completes.
        _writeConfigFile(CONFIGURATION_FILE_NAME);
        _writeDataDirectoryVersion();

        nanoTimer.stop();
        Logger.debug("Database installed in " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    @Override
    public void upgrade() throws Exception {
        Thread.sleep(2500L);

        final File installationDirectory = _databaseProperties.getInstallationDirectory();
        final String rootPassword = _databaseProperties.getRootPassword();

        final String displayCommand;
        final String[] command = new String[3];
        {
            final File file = new File(installationDirectory.getPath() + "/upgrade.bat");
            if (! (file.isFile() && file.canExecute())) {
                Logger.warn("Unable to upgrade mysql tables, upgrade script not found.");
                return;
            }

            final Integer port = _databaseProperties.getPort();
            final String portString = (port != null ? port.toString() : "");
            final String nonNullRootPassword = (rootPassword != null ? rootPassword : "");

            command[0] = file.getPath();
            command[1] = portString;
            command[2] = nonNullRootPassword;
            displayCommand  = (file.getPath() + " " + portString + " " + "<password>");
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + displayCommand);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (final InputStream inputStream = process.getInputStream()) {
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
            _shutdownHookInstalled = true;
        }

        _writeConfigFile(CONFIGURATION_FILE_NAME);

        final File installationDirectory = _databaseProperties.getInstallationDirectory();

        final Long javaPid = SystemUtil.getProcessId();

        final String[] command = new String[2];
        {
            final File file = new File(installationDirectory.getPath() + "/run.bat");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to start database. Run script not found.");
            }
            command[0] = file.getPath();
            command[1] = javaPid.toString();
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.debug("Exec: " + String.join(" ", command));
        _process = runtime.exec(command);

        _processOutputStream = _process.getOutputStream();
        _processInputStream = _process.getInputStream();
        _processInputReadThread = new ProcessOutputLogger(_processInputStream, Logger.getInstance(this.getClass()));
        _processInputReadThread.start();
    }
}
