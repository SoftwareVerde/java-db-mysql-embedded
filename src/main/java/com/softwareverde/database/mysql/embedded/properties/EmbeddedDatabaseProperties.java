package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.constable.list.List;
import com.softwareverde.database.jdbc.JdbcDatabaseProperties;
import com.softwareverde.database.mysql.embedded.OperatingSystemType;

import java.io.File;

public interface EmbeddedDatabaseProperties extends JdbcDatabaseProperties {
    OperatingSystemType getOperatingSystemType();
    File getInstallationDirectory();
    File getDataDirectory();

    List<String> getCommandlineArguments();

    /**
     * Returns the serialized contents of the DatabaseProperties, in the format of a MySQL configuration file.
     *  Often referred to as my.conf / mysql.conf / my.ini, and sometimes as the "DefaultsFile" within the MySQL documentation.
     */
    String getMysqlConfigurationFileContents();
}
