package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.DatabaseConnection;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;
import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Version;

import java.io.File;
import java.sql.Connection;

public class Main {
    public static void main(final String[] parameters) {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.DEBUG);

        if (parameters.length < 3) {
            System.err.println("Usage: <installationDirectory> <dataDirectory> <mysqlRootPassword>");
            System.exit(1);
        }

        final File installationDirectory = new File(parameters[0]);
        final File dataDirectory = new File(parameters[1]);
        final String mysqlRootPassword = parameters[2];

        final MutableEmbeddedDatabaseProperties databaseProperties = new MutableEmbeddedDatabaseProperties();
        databaseProperties.setHostname("127.0.0.1");
        databaseProperties.setPort(MysqlDatabase.DEFAULT_PORT);
        databaseProperties.setRootPassword(mysqlRootPassword);
        databaseProperties.setUsername("user");
        databaseProperties.setPassword("password");
        databaseProperties.setSchema("example");
        databaseProperties.setDataDirectory(dataDirectory);
        databaseProperties.setInstallationDirectory(installationDirectory);

        final Integer databaseVersion = 1;
        final DatabaseInitializer<Connection> databaseInitializer = new MysqlDatabaseInitializer(null, databaseVersion, new DatabaseInitializer.DatabaseUpgradeHandler<Connection>() {
            @Override
            public Boolean onUpgrade(final DatabaseConnection<Connection> maintenanceDatabaseConnection, final Integer previousVersion, final Integer requiredVersion) {
                return (previousVersion <= databaseVersion);
            }
        });

        EmbeddedMysqlDatabase embeddedMysqlDatabase = null;
        try {
            embeddedMysqlDatabase = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer);

            final Version mysqlVersion = embeddedMysqlDatabase.getInstallationDirectoryVersion();
            final Version mysqlDataVersion = embeddedMysqlDatabase.getDataDirectoryVersion();
            Logger.info("Mysql Version: " + mysqlVersion);
            Logger.info("Mysql Data Version: " + mysqlDataVersion);

            if (embeddedMysqlDatabase.isInstallationRequired()) {
                Logger.info("Installing database.");
                embeddedMysqlDatabase.install();
            }

            embeddedMysqlDatabase.start();
            Logger.info("Database online.");

            final Thread thread = Thread.currentThread();
            while (! thread.isInterrupted()) {
                Thread.sleep(1000L);
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
        finally {
            try {
                if (embeddedMysqlDatabase != null) {
                    embeddedMysqlDatabase.stop();
                }
            }
            catch (final Exception exception) { }
        }
    }
}
