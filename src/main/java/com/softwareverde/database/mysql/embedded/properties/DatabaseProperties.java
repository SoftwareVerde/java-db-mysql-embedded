package com.softwareverde.database.mysql.embedded.properties;

public class DatabaseProperties {
    protected String _rootPassword;
    protected String _connectionUrl;
    protected String _username;
    protected String _password;
    protected String _schema;
    protected Integer _port;
    protected String _dataDirectory;

    public String getRootPassword() { return  _rootPassword; }
    public String getConnectionUrl() { return _connectionUrl; }
    public String getUsername() { return _username; }
    public String getPassword() { return _password; }
    public String getSchema() { return _schema; }
    public Integer getPort() { return _port; }
    public String getDataDirectory() { return _dataDirectory; }

    public void setRootPassword(final String rootPassword) { _rootPassword = rootPassword; }
    public void setConnectionUrl(final String connectionUrl) { _connectionUrl = connectionUrl; }
    public void setUsername(final String username) { _username = username; }
    public void setPassword(final String password) { _password = password; }
    public void setSchema(final String schema) { _schema = schema; }
    public void setPort(final Integer port) { _port = port; }
    public void setDataDirectory(final String dataDirectory) { _dataDirectory = dataDirectory; }
}
