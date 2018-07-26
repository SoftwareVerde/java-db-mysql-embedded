package com.softwareverde.database.mysql.embedded.vorburger;

public class DBConfigurationBuilder {
    protected final ch.vorburger.mariadb4j.DBConfigurationBuilder _dbConfigurationBuilder;

    protected DBConfigurationBuilder(final ch.vorburger.mariadb4j.DBConfigurationBuilder dbConfigurationBuilder) {
        _dbConfigurationBuilder = dbConfigurationBuilder;
    }

    public static DBConfigurationBuilder newBuilder() {
        final ch.vorburger.mariadb4j.DBConfigurationBuilder rawDbConfigurationBuilder = ch.vorburger.mariadb4j.DBConfigurationBuilder.newBuilder();
        return new DBConfigurationBuilder(rawDbConfigurationBuilder);
    }

    public DBConfiguration build() {
        return new DBConfiguration(_dbConfigurationBuilder.build());
    }

    public void setPort(final int port) {
        _dbConfigurationBuilder.setPort(port);
    }

    public void setDataDir(final String dataDirectory) {
        _dbConfigurationBuilder.setDataDir(dataDirectory);
    }

    public void setSecurityDisabled(final boolean securityIsDisabled) {
        _dbConfigurationBuilder.setSecurityDisabled(securityIsDisabled);
    }

    public String getURL(final String databaseName) {
        return _dbConfigurationBuilder.getURL(databaseName);
    }

    public void addArgument(final String argument) {
        _dbConfigurationBuilder.addArg(argument);
    }

    public void addInstallationArgument(final String installationArgument) {
        _dbConfigurationBuilder.addInstallArg(installationArgument);
    }
}