package com.softwareverde.database.mysql.embedded.vorburger;

import ch.vorburger.exec.ManagedProcessListener;

public class DatabaseConfigurationBuilder {
    protected final ch.vorburger.mariadb4j.DBConfigurationBuilder _dbConfigurationBuilder;

    protected DatabaseConfigurationBuilder(final ch.vorburger.mariadb4j.DBConfigurationBuilder dbConfigurationBuilder) {
        _dbConfigurationBuilder = dbConfigurationBuilder;
    }

    public static DatabaseConfigurationBuilder newBuilder() {
        final ch.vorburger.mariadb4j.DBConfigurationBuilder rawDbConfigurationBuilder = ch.vorburger.mariadb4j.DBConfigurationBuilder.newBuilder();
        return new DatabaseConfigurationBuilder(rawDbConfigurationBuilder);
    }

    public Integer getPort() {
        return _dbConfigurationBuilder.getPort();
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

    public void setShutdownHook(final Runnable shutdownHook) {
        _dbConfigurationBuilder.setProcessListener(new ManagedProcessListener() {
            @Override
            public void onProcessComplete(final int exitValue) {
                if (shutdownHook != null) {
                    shutdownHook.run();
                }
            }

            @Override
            public void onProcessFailed(final int exitValue, final Throwable throwable) {
                if (shutdownHook != null) {
                    shutdownHook.run();
                }
            }
        });
    }
}