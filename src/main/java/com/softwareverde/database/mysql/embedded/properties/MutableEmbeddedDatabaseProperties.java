package com.softwareverde.database.mysql.embedded.properties;

import com.softwareverde.database.mysql.embedded.OperatingSystemType;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.database.properties.MutableDatabaseProperties;

import java.io.File;

public class MutableEmbeddedDatabaseProperties extends MutableDatabaseProperties implements EmbeddedDatabaseProperties {
    protected OperatingSystemType _operatingSystemType;
    protected File _installationDirectory;
    protected File _dataDirectory;

    public MutableEmbeddedDatabaseProperties() {
        _operatingSystemType = OperatingSystemType.getOperatingSystemType();
    }

    public MutableEmbeddedDatabaseProperties(final DatabaseProperties databaseProperties) {
        super(databaseProperties);
        _operatingSystemType = OperatingSystemType.getOperatingSystemType();
    }

    @Override
    public OperatingSystemType getOperatingSystemType() {
        return _operatingSystemType;
    }

    @Override
    public File getInstallationDirectory() {
        return _installationDirectory;
    }

    @Override
    public File getDataDirectory() {
        return _dataDirectory;
    }

    public void setOperatingSystemType(final OperatingSystemType operatingSystemType) {
        _operatingSystemType = operatingSystemType;
    }

    public void setInstallationDirectory(final File installationDirectory) {
        _installationDirectory = installationDirectory;
    }

    public void setDataDirectory(final File dataDirectory) {
        _dataDirectory = dataDirectory;
    }
}
