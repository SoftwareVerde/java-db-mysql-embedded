package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.mysql.embedded.OperatingSystemType;
import com.softwareverde.database.properties.DatabaseProperties;

import java.io.File;

public interface EmbeddedDatabaseProperties extends DatabaseProperties {
    OperatingSystemType getOperatingSystemType();
    File getInstallationDirectory();
    File getDataDirectory();
}
