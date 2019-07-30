package com.softwareverde.database.mysql.embedded;


import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfiguration;
import com.softwareverde.database.mysql.embedded.vorburger.DatabaseConfigurationBuilder;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.query.Query;

import java.sql.Connection;
import java.util.Properties;

public class EmbeddedMysqlDatabase extends MysqlDatabase {
    protected DB _databaseInstance;
    protected Runnable _onShutdownCallback = null;

    protected DB _startDatabaseInstance(final DBConfiguration dbConfiguration, final Long timeoutMilliseconds) throws DatabaseException {
        try {
            final DB db = DB.newEmbeddedDB(dbConfiguration);
            db.start(timeoutMilliseconds);
            return db;
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    protected void _hardenDatabase(final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
    }

    protected void _initializeRootAccount(final MysqlDatabaseConnection databaseConnection, final DatabaseCredentials rootCredentials) throws DatabaseException {
        final String rootHost = "127.0.0.1";

        { // Restrict root to localhost and set root password...
            databaseConnection.executeSql(
                new Query("DELETE FROM mysql.user WHERE user != ? OR host != ?")
                    .setParameter(rootCredentials.username)
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

    protected void _createSchema(final MysqlDatabaseConnection databaseConnection, final String schema) throws DatabaseException {
        databaseConnection.executeDdl("CREATE DATABASE IF NOT EXISTS `" + schema + "`");
    }

    protected void _loadDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments, final Properties connectionProperties, final Long timeoutMilliseconds) throws DatabaseException {
        final DatabaseCredentials defaultRootCredentials = new DatabaseCredentials("root", "");
        final DatabaseCredentials rootCredentials = new DatabaseCredentials("root", databaseProperties.getRootPassword());
        // final Credentials credentials = new Credentials(databaseProperties.getUsername(), databaseProperties.getPassword());;

        final DBConfiguration dbConfiguration;
        {
            final DatabaseConfigurationBuilder configBuilder = DatabaseConfigurationBuilder.newBuilder();
            configBuilder.setPort(databaseProperties.getPort());
            configBuilder.setDataDir(databaseProperties.getDataDirectory());
            configBuilder.setSecurityDisabled(false);

            for (final String argument : databaseCommandLineArguments.getArguments()) {
                configBuilder.addArgument(argument);
            }

            for (final String installationArgument : databaseCommandLineArguments.getInstallationArguments()) {
                configBuilder.addInstallationArgument(installationArgument);
            }

            configBuilder.setShutdownHook(new Runnable() {
                @Override
                public void run() {
                    final Runnable onShutdownCallback = _onShutdownCallback;
                    if (onShutdownCallback != null) {
                        onShutdownCallback.run();
                    }
                }
            });

            dbConfiguration = configBuilder.build();
        }

        final DB databaseInstance = _startDatabaseInstance(dbConfiguration, timeoutMilliseconds);

        DatabaseException setupException = null;
        // Set the root account credentials, setup maintenance account, and harden the database...
        final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties.getHostname(), databaseProperties.getPort(), "", defaultRootCredentials.username, defaultRootCredentials.password, connectionProperties);
        try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
            try {
                final Integer databaseVersionNumber = databaseInitializer.getDatabaseVersionNumber(rootDatabaseConnection);
                if (databaseVersionNumber == 0) {
                    _initializeRootAccount(rootDatabaseConnection, rootCredentials);
                    _createSchema(rootDatabaseConnection, databaseProperties.getSchema());
                    databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
                    _hardenDatabase(rootDatabaseConnection);
                }
            }
            catch (final DatabaseException databaseException) {
                // The setup failed, which should cause the initialization to fail..
                setupException = databaseException;
            }
        }
        catch (final DatabaseException exception) {
            // The default root credentials failed, which is likely because the database has already been configured...
        }

        if (setupException != null) { throw setupException; }

        final DatabaseCredentials maintenanceCredentials = databaseInitializer.getMaintenanceCredentials(databaseProperties);

        // Switch over to the maintenance account for the database initialization...
        final MysqlDatabaseConnectionFactory maintenanceCredentialsDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties, maintenanceCredentials, connectionProperties);
        try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceCredentialsDatabaseConnectionFactory.newConnection()) {
            databaseInitializer.initializeDatabase(maintenanceDatabaseConnection);
        }

        _databaseInstance = databaseInstance;
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties) throws DatabaseException {
        super(databaseProperties);
        final MysqlDatabaseInitializer databaseInitializer = new MysqlDatabaseInitializer();
        final DatabaseCommandLineArguments databaseCommandLineArguments = new DatabaseCommandLineArguments();
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties(), Long.MAX_VALUE);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer) throws DatabaseException {
        super(databaseProperties);
        final DatabaseCommandLineArguments databaseCommandLineArguments = new DatabaseCommandLineArguments();
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties(), Long.MAX_VALUE);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments) throws DatabaseException {
        super(databaseProperties);
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties(), Long.MAX_VALUE);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments, final Properties connectionProperties) throws DatabaseException {
        super(databaseProperties);
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, connectionProperties, Long.MAX_VALUE);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments, final Properties connectionProperties, final Long maxStartupTimeoutMilliseconds) throws DatabaseException {
        super(databaseProperties);
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, connectionProperties, maxStartupTimeoutMilliseconds);
    }

    public void setOnShutdownCallback(final Runnable onShutdownCallback) {
        _onShutdownCallback = onShutdownCallback;
    }
}
