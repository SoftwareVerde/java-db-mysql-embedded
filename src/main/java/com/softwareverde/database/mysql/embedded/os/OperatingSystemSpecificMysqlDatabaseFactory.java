package com.softwareverde.database.mysql.embedded.os;

import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;

public interface OperatingSystemSpecificMysqlDatabaseFactory {
    OperatingSystemSpecificMysqlDatabase newInstance(EmbeddedDatabaseProperties embeddedDatabaseProperties);
}
