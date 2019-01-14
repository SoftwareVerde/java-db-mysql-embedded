package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.mysql.properties.DatabaseProperties;

public interface EmbeddedDatabaseProperties extends DatabaseProperties {
    String getDataDirectory();

    @Override
    ImmutableEmbeddedDatabaseProperties asConst();
}