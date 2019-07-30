package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.properties.MutableDatabaseProperties;

public class MutableEmbeddedDatabaseProperties extends MutableDatabaseProperties implements EmbeddedDatabaseProperties {
    protected String _dataDirectory;

    public MutableEmbeddedDatabaseProperties() { }

    public MutableEmbeddedDatabaseProperties(final EmbeddedDatabaseProperties databaseProperties) {
        super(databaseProperties);
        _dataDirectory = databaseProperties.getDataDirectory();
    }

    public void setDataDirectory(final String dataDirectory) {
        _dataDirectory = dataDirectory;
    }

    @Override
    public String getDataDirectory() {
        return _dataDirectory;
    }
}
