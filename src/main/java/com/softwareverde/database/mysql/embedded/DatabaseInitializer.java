package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.vorburger.DB;

import java.util.List;

public class DatabaseInitializer {
    public interface DatabaseUpgradeHandler {
        Boolean onUpgrade(int previousVersion, int requiredVersion);
    }

    protected final String _initSqlFileName;
    protected final Integer _requiredDatabaseVersion;
    protected final DatabaseUpgradeHandler _databaseUpgradeHandler;

    public DatabaseInitializer() {
        _initSqlFileName = null;
        _requiredDatabaseVersion = 1;
        _databaseUpgradeHandler = new DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int previousVersion, final int requiredVersion) {
                throw new RuntimeException("Database upgrade not supported.");
            }
        };
    }

    public DatabaseInitializer(final String databaseInitFileName, final Integer requiredDatabaseVersion, final DatabaseUpgradeHandler databaseUpgradeHandler) {
        _initSqlFileName = databaseInitFileName;
        _requiredDatabaseVersion = requiredDatabaseVersion;
        _databaseUpgradeHandler = databaseUpgradeHandler;
    }

    public void initializeDatabase(final DB databaseInstance, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final Credentials maintenanceCredentials) throws DatabaseException {
        final Integer databaseVersionNumber;
        {
            Integer versionNumber = 0;
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                final List<Row> rows = databaseConnection.query("SELECT version FROM metadata ORDER BY id DESC LIMIT 1", null);
                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    versionNumber = row.getInteger("version");
                }
            }
            catch (final Exception exception) { }
            databaseVersionNumber = versionNumber;
        }

        try {
            if (databaseVersionNumber < 1) {
                databaseInstance.source("queries/metadata_init.sql", maintenanceCredentials.username, maintenanceCredentials.password, maintenanceCredentials.schema);
                if (_initSqlFileName != null) {
                    databaseInstance.source(_initSqlFileName, maintenanceCredentials.username, maintenanceCredentials.password, maintenanceCredentials.schema);
                }
            }
            else if (databaseVersionNumber < _requiredDatabaseVersion) {
                final Boolean upgradeWasSuccessful = _databaseUpgradeHandler.onUpgrade(databaseVersionNumber, _requiredDatabaseVersion);
                if (! upgradeWasSuccessful) {
                    throw new RuntimeException("Unable to upgrade database from v" + databaseVersionNumber + " to v" + _requiredDatabaseVersion + ".");
                }
            }
        }
        catch (final DatabaseException exception) {
            throw new DatabaseException(exception);
        }
    }
}
