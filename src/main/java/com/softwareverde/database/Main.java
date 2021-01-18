package com.softwareverde.database;

import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

import java.io.File;

public class Main {
    public static void main(final String[] parameters) {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);

        if (parameters.length < 3) {
            System.err.println("Usage: <dataDirectory> <installationDirectory> <mysqlRootPassword>");
            System.exit(1);
        }

        final MysqlDatabaseConfiguration databaseConfiguration = new MysqlDatabaseConfiguration();
        databaseConfiguration.setPort(MysqlDatabaseConfiguration.DEFAULT_PORT);

        try {
            final OperatingSystemType operatingSystemType = OperatingSystemType.getOperatingSystemType();
            final File installationDirectory = new File(parameters[0]);
            final File dataDirectory = new File(parameters[1]);
            final String mysqlRootPassword = parameters[2];

            final MariaDb mariaDb = new MariaDb(operatingSystemType, installationDirectory, dataDirectory);
            mariaDb.setDatabaseConfiguration(databaseConfiguration);
            mariaDb.install(mysqlRootPassword);
            mariaDb.start();

            final Thread thread = Thread.currentThread();
            while (! thread.isInterrupted()) {
                Thread.sleep(1000L);
            }

            mariaDb.stop();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }
}
