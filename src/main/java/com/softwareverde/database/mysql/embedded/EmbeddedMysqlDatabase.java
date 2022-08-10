package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.os.OperatingSystemSpecificMysqlDatabase;
import com.softwareverde.database.mysql.embedded.os.OperatingSystemSpecificMysqlDatabaseFactory;
import com.softwareverde.database.mysql.embedded.os.OperatingSystemType;
import com.softwareverde.database.mysql.embedded.os.UnixMysqlDatabase;
import com.softwareverde.database.mysql.embedded.os.WindowsMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.database.query.Query;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.Version;
import com.softwareverde.util.timer.NanoTimer;

import java.sql.Connection;
import java.util.Properties;

public class EmbeddedMysqlDatabase extends MysqlDatabase {
    protected static final OperatingSystemSpecificMysqlDatabaseFactory DEFAULT_DATABASE_FACTORY = new OperatingSystemSpecificMysqlDatabaseFactory() {
        @Override
        public OperatingSystemSpecificMysqlDatabase newInstance(final EmbeddedDatabaseProperties databaseProperties) {
            final OperatingSystemType operatingSystemType = databaseProperties.getOperatingSystemType();
            if (operatingSystemType == OperatingSystemType.WINDOWS) {
                return new WindowsMysqlDatabase(databaseProperties);
            }

            return new UnixMysqlDatabase(databaseProperties);
        }
    };

    protected static void rethrowException(final Exception exception) throws DatabaseException {
        if (exception instanceof DatabaseException) {
            throw (DatabaseException) exception;
        }

        throw new DatabaseException(exception);
    }

    protected final OperatingSystemSpecificMysqlDatabase _delegate;
    protected final EmbeddedDatabaseProperties _databaseProperties;
    protected final DatabaseInitializer<Connection> _databaseInitializer;

    protected void _deleteTestDatabase(final MysqlDatabaseConnection databaseConnection) throws Exception {
        databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
        databaseConnection.executeSql(new Query("DELETE FROM mysql.db WHERE db = 'test' OR db = 'test\\_%'"));
    }

    protected void _removeAnonymousAccounts(final MysqlDatabaseConnection databaseConnection) throws Exception {
        { // Restrict root to localhost and set root password...
            databaseConnection.executeSql(
                new Query("DELETE FROM mysql.global_priv WHERE user=''")
            );

            databaseConnection.executeSql(new Query("FLUSH PRIVILEGES"));
        }
    }

    protected void _initializeRootAccount(final MysqlDatabaseConnection databaseConnection, final DatabaseCredentials rootCredentials) throws Exception {
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

    /**
     * Returns a root-user ConnectionFactory that has been tested for validity.
     *  Due to various possible states of database installation, the root user may be insecure or already configured
     *  with a root password; this function returns the appropriate ConnectionFactory for either case.
     */
    protected MysqlDatabaseConnectionFactory _getRootDatabaseConnectionFactory(final DatabaseProperties databaseProperties, final Properties connectionProperties) {
        final String username = "root";
        final String hostname = databaseProperties.getHostname();
        final Integer port = databaseProperties.getPort();
        final String schema = "";

        final Query testQuery = new Query("SELECT 1");

        { // Attempt to connect via an empty root password...
            final DatabaseCredentials rootCredentials = new DatabaseCredentials(username, "");
            final MysqlDatabaseConnectionFactory emptyRootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(hostname, port, schema, rootCredentials.username, rootCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection databaseConnection = emptyRootDatabaseConnectionFactory.newConnection()) {
                databaseConnection.query(testQuery);
                return emptyRootDatabaseConnectionFactory;
            }
            catch (final DatabaseException exception) { }
        }

        { // Attempt to connect via the configured root password...
            final DatabaseCredentials rootCredentials = new DatabaseCredentials(username, databaseProperties.getRootPassword());
            final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(hostname, port, schema, rootCredentials.username, rootCredentials.password, connectionProperties);
            try (final MysqlDatabaseConnection databaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                databaseConnection.query(testQuery);
                return rootDatabaseConnectionFactory;
            }
            catch (final DatabaseException exception) { }
        }

        return null;
    }

    /**
     * Runs the databaseInitializer which handles application-level data upgrade triggers.
     */
    protected void _initializeDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final Properties connectionProperties) throws Exception {
        final Integer databaseVersionNumber = _getDatabaseVersionNumber();
        if (databaseVersionNumber == 0) {
            Logger.info("Initializing database.");

            // If the database version wasn't able to be obtained initially then assume the database needs to be setup for its first run.
            final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = _getRootDatabaseConnectionFactory(databaseProperties, connectionProperties);
            if (rootDatabaseConnectionFactory == null) { throw new DatabaseException("Unable to connect to database via root."); }
            try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                final DatabaseCredentials rootCredentials = new DatabaseCredentials("root", databaseProperties.getRootPassword());

                _initializeRootAccount(rootDatabaseConnection, rootCredentials);
                _deleteTestDatabase(rootDatabaseConnection);
                _removeAnonymousAccounts(rootDatabaseConnection);
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

    protected void _start() throws Exception {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        _delegate.start();

        final Version installationDirectoryVersion = _delegate.getInstallationDirectoryVersion();
        final Version dataDirectoryVersion = _delegate.getDataDirectoryVersion();

        if (installationDirectoryVersion == null) { throw new DatabaseException("Database must be installed before it can be started."); }
        final boolean willUpgrade = (! Util.areEqual(installationDirectoryVersion, dataDirectoryVersion));

        final Long timeoutMs = (willUpgrade ? _delegate.getUpgradeTimeoutMs() : _delegate.getTimeoutMs());
        _delegate.waitForDatabaseToComeOnline(timeoutMs);

        if (willUpgrade) {
            _delegate.upgrade();
        }

        _initializeDatabase(_databaseProperties, _databaseInitializer, _connectionProperties);

        nanoTimer.stop();
        Logger.debug("Database came online after " + nanoTimer.getMillisecondsElapsed() + "ms.");
    }

    protected EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer, final OperatingSystemSpecificMysqlDatabaseFactory databaseFactory) {
        super(
            databaseProperties.getHostname(),
            databaseProperties.getPort(),
            databaseProperties.getCredentials().username,
            databaseProperties.getCredentials().password,
            databaseProperties.getConnectionProperties()
        );

        _schema = databaseProperties.getSchema();
        _databaseProperties = databaseProperties;
        _databaseInitializer = databaseInitializer;
        _delegate = databaseFactory.newInstance(databaseProperties);
    }

    public EmbeddedMysqlDatabase(final EmbeddedDatabaseProperties databaseProperties, final DatabaseInitializer<Connection> databaseInitializer) {
        this(databaseProperties, databaseInitializer, DEFAULT_DATABASE_FACTORY);
    }

    public void setTimeout(final Long timeoutMs) {
        _delegate.setTimeoutMs(timeoutMs);
    }

    public void setUpgradeTimeout(final Long timeoutMs) {
        _delegate.setUpgradeTimeout(timeoutMs);
    }

    /**
     * Attempts to install the database binaries and data files.
     *  Install will also write/update the configuration files and version files.
     */
    public void install() throws DatabaseException {
        try {
            _delegate.install();
        }
        catch (final Exception exception) {
            EmbeddedMysqlDatabase.rethrowException(exception);
        }
    }

    /**
     * Starts the embedded database and blocks until the database is online.
     *  If the database has not been installed, it will attempt to install the database binaries, data files,
     *  configuration files, and version files.
     */
    public void start() throws DatabaseException {
        this.start(false);
    }

    public void start(final Boolean skipInstall) throws DatabaseException {
        try {
            if (! skipInstall) {
                final Boolean isInstalled = _delegate.isInstalled();
                if (! isInstalled) {
                    _delegate.install();
                }
            }

            _start();
        }
        catch (final Exception exception) {
            EmbeddedMysqlDatabase.rethrowException(exception);
        }
    }

    /**
     * Shuts the database down and blocks until the database has gone offline or until the timeout is reached.
     */
    public void stop() throws DatabaseException {
        try {
            _delegate.stop();
        }
        catch (final Exception exception) {
            EmbeddedMysqlDatabase.rethrowException(exception);
        }
    }

    /**
     * Returns true if the database binaries and database data files have been installed with the packaged version.
     */
    public Boolean isInstallationRequired() {
        final Boolean isInstalled = _delegate.isInstalled();
        return (! isInstalled);
    }

    /**
     * Returns the Version of the installed database binaries or null if an installation was not found.
     */
    public Version getInstallationDirectoryVersion() {
        return _delegate.getInstallationDirectoryVersion();
    }

    /**
     * Returns the Version of the database binaries that were installed when the database last successfully started
     *  or when the database data files were installed.  This is expected to be the same as the database tables' version.
     *  Returns null if the data directory was not initialized.
     */
    public Version getDataDirectoryVersion() {
        return _delegate.getDataDirectoryVersion();
    }
}
