package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.mysql.properties.ImmutableDatabaseProperties;

public class ImmutableEmbeddedDatabaseProperties extends ImmutableDatabaseProperties implements EmbeddedDatabaseProperties {
    protected String _dataDirectory;

    public ImmutableEmbeddedDatabaseProperties(final EmbeddedDatabaseProperties databaseProperties) {
        super(databaseProperties);
        _dataDirectory = databaseProperties.getDataDirectory();
    }

    @Override
    public String getDataDirectory() {
        return _dataDirectory;
    }

    @Override
    public ImmutableEmbeddedDatabaseProperties asConst() { return this; }
}
