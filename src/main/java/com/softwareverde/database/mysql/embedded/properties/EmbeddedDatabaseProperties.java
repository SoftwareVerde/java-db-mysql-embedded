package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.properties.DatabaseProperties;

public interface EmbeddedDatabaseProperties extends DatabaseProperties {
    String getDataDirectory();
}