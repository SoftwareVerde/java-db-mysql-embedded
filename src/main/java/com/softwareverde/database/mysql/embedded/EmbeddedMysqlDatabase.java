package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.Database;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfiguration;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfigurationBuilder;
import com.softwareverde.util.HashUtil;

import java.sql.Connection;
import java.util.Properties;

public class EmbeddedMysqlDatabase implements Database<Connection> {
    protected DB _databaseInstance;
    protected MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    protected void _loadDatabase(final DatabaseProperties databaseProperties, final DatabaseInitializer databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments, final Properties connectionProperties) throws DatabaseException {
        final String rootHost = "127.0.0.1";

        final Credentials defaultRootCredentials;
        final Credentials rootCredentials;
        final Credentials credentials;
        final Credentials maintenanceCredentials;
        {
            final String databaseSchema = databaseProperties.getSchema();
            final String rootUsername = "root";
            final String defaultRootPassword = "";
            final String newRootPassword = databaseProperties.getRootPassword();
            final String maintenanceUsername = (databaseSchema + "_maintenance");
            final String maintenancePassword = HashUtil.sha256(newRootPassword);

            defaultRootCredentials = new Credentials(rootUsername, defaultRootPassword, databaseSchema);
            rootCredentials = new Credentials(rootUsername, newRootPassword, databaseSchema);
            credentials = new Credentials(databaseProperties.getUsername(), databaseProperties.getPassword(), databaseSchema);
            maintenanceCredentials = new Credentials(maintenanceUsername, maintenancePassword, databaseSchema);
        }

        final MysqlDatabaseConnectionFactory defaultCredentialsDatabaseConnectionFactory;
        final MysqlDatabaseConnectionFactory databaseConnectionFactory;
        final DBConfiguration dbConfiguration;
        {
            final DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(databaseProperties.getPort());
            configBuilder.setDataDir(databaseProperties.getDataDirectory());
            configBuilder.setSecurityDisabled(false);
            for (final String argument : databaseCommandLineArguments.getArguments()) {
                configBuilder.addArgument(argument);
            }
            dbConfiguration = configBuilder.build();

            final String connectionString = configBuilder.getURL(credentials.schema);
            databaseConnectionFactory = new MysqlDatabaseConnectionFactory(connectionString, credentials.username, credentials.password, connectionProperties);

            final String defaultCredentialsConnectionString = configBuilder.getURL(""); // NOTE: Should empty string (cannot be null).
            defaultCredentialsDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(defaultCredentialsConnectionString, defaultRootCredentials.username, defaultRootCredentials.password);
        }

        final DB databaseInstance;
        {
            DB db = null;
            try {
                db = DB.newEmbeddedDB(dbConfiguration);
                db.start();
            }
            catch (final Exception exception) {
                throw new DatabaseException(exception);
            }
            databaseInstance = db;
        }

        { // Check for default username/password...
            Boolean databaseIsConfigured = false;
            DatabaseException databaseConfigurationFailureReason = null;
            try (final MysqlDatabaseConnection databaseConnection = defaultCredentialsDatabaseConnectionFactory.newConnection()) {
                try {
                    databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
                    databaseConnection.executeDdl("CREATE DATABASE IF NOT EXISTS `"+ credentials.schema +"`");

                    { // Restrict root to localhost and set root password...
                        databaseConnection.executeSql(
                            new Query("DELETE FROM mysql.user WHERE user != ? OR host != ?")
                                .setParameter(defaultRootCredentials.username)
                                .setParameter(rootHost)
                        );
                        databaseConnection.executeSql(
                            new Query("ALTER USER ?@? IDENTIFIED BY ?")
                                .setParameter(rootCredentials.username)
                                .setParameter(rootHost)
                                .setParameter(rootCredentials.password)
                        );
                    }

                    { // Create maintenance user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(maintenanceCredentials.username)
                                .setParameter(maintenanceCredentials.password)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT ALL PRIVILEGES ON `" + maintenanceCredentials.schema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(maintenanceCredentials.username)
                                .setParameter(maintenanceCredentials.password)
                        );
                    }

                    { // Create regular user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(credentials.username)
                                .setParameter(credentials.password)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT SELECT, INSERT, DELETE, UPDATE, EXECUTE ON `" + credentials.schema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(credentials.username)
                                .setParameter(credentials.password)
                        );
                    }

                    databaseConnection.executeSql("FLUSH PRIVILEGES", null);
                    databaseIsConfigured = true;
                }
                catch (final Exception exception) {
                    databaseIsConfigured = false;
                    databaseConfigurationFailureReason = new DatabaseException(exception);
                }
            }
            catch (final DatabaseException exception) {
                databaseIsConfigured = true;
            }

            if (! databaseIsConfigured) {
                throw databaseConfigurationFailureReason;
            }
        }

        databaseInitializer.initializeDatabase(databaseInstance, databaseConnectionFactory, maintenanceCredentials);

        _databaseInstance = databaseInstance;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public EmbeddedMysqlDatabase(final DatabaseProperties databaseProperties) throws DatabaseException {
        final DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        final DatabaseCommandLineArguments databaseCommandLineArguments = new DatabaseCommandLineArguments();
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties());
    }

    public EmbeddedMysqlDatabase(final DatabaseProperties databaseProperties, final DatabaseInitializer databaseInitializer) throws DatabaseException {
        final DatabaseCommandLineArguments databaseCommandLineArguments = new DatabaseCommandLineArguments();
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties());
    }

    public EmbeddedMysqlDatabase(final DatabaseProperties databaseProperties, final DatabaseInitializer databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments) throws DatabaseException {
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, new Properties());
    }

    public EmbeddedMysqlDatabase(final DatabaseProperties databaseProperties, final DatabaseInitializer databaseInitializer, final DatabaseCommandLineArguments databaseCommandLineArguments, final Properties connectionProperties) throws DatabaseException {
        _loadDatabase(databaseProperties, databaseInitializer, databaseCommandLineArguments, connectionProperties);
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        return _databaseConnectionFactory.newConnection();
    }

    public MysqlDatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }
}
