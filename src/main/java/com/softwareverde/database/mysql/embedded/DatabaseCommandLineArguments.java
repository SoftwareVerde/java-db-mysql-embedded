package com.softwareverde.database.mysql.embedded;

import com.softwareverde.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Represents MariaDB/MySQL command-line arguments.</p>
 *
 * <p>Provides a number of setters that can be used to specify certain commonly set values in a type-safe manner.  In an
 * effort to ensure that the purpose of the arguments are clear, the setters do not necessarily have names that match
 * the command-line argument names.  Calling a setter with a value of <code>null</code> will have the effect of un-setting
 * that argument.  When possible, new setters should be created in lieu of calling the more generic <code>addArgument</code>
 * method, which is provided to account for the fact that it would not be practical to maintain a full up-to-date list
 * of available command-line arguments.</p>
 */
public class DatabaseCommandLineArguments {
    private List<String> _arguments;
    private Long _maxAllowedPacketByteCount;
    private Long _keyBufferByteCount;
    private Long _threadStackNestedCallLimit;
    private Long _threadCacheThreadCount;
    private Long _tableOpenCacheTableCount;
    private Long _maxHeapTableByteCount;
    private Long _queryCacheMaxResultByteCount;
    private Long _queryCacheByteCount;
    private Integer _innoDbBufferPoolInstanceCount;
    private Long _innoDbBufferPoolByteCount;
    private Long _innoDbLogFileByteCount;
    private Long _innoDbLogBufferByteCount;

    private Boolean _innoDbSlowQueryLogIsEnabled;
    private Long _innoDbSlowQueryLogMinimumQueryTime;
    private String _innoDbSlowQueryLogFileName;

    private List<String> _installationArguments;
    private Integer _innoDbForceRecoveryLevel;

    public DatabaseCommandLineArguments() {
        _arguments = new ArrayList<String>();
        _installationArguments = new ArrayList<String>();
    }

    /**
     * <p>Allows for adding an arbitrary string argument to the command-line.  In general, the property-specific setters
     * should be preferred to this method but when those are not sufficient and an appropriate setter cannot be added,
     * this method provides a fall-back.</p>
     *
     * <p>This argument must be fully specified (i.e. including "--" prefix, value assignment, etc.).  Arguments added
     * via this method will appear before those added by the setters.  The arguments from the setters will always appear
     * after arguments set with this method.</p>
     * @param argument
     */
    public void addArgument(final String argument) {
        _arguments.add(argument);
    }

    /**
     * Allows for adding arbitrary string arguments to the command-line during execution of mysql_install_db.
     * This functions nearly identically to addArgument(String).
     */
    public void addInstallationArgument(final String argument) {
        _installationArguments.add(argument);
    }

    public void setMaxAllowedPacketByteCount(final Long maxAllowedPacketByteCount) {
        _maxAllowedPacketByteCount = maxAllowedPacketByteCount;
    }

    public void setKeyBufferByteCount(final Long keyBufferByteCount) {
        _keyBufferByteCount = keyBufferByteCount;
    }

    public void setThreadStackNestedCallLimit(final Long threadStackNestedCallLimit) {
        _threadStackNestedCallLimit = threadStackNestedCallLimit;
    }

    public void setThreadCacheThreadCount(final Long threadCacheThreadCount) {
        _threadCacheThreadCount = threadCacheThreadCount;
    }

    public void setTableOpenCacheTableCount(final Long tableOpenCacheTableCount) {
        _tableOpenCacheTableCount = tableOpenCacheTableCount;
    }

    public void setMaxHeapTableByteCount(final Long maxHeapTableByteCount) {
        _maxHeapTableByteCount = maxHeapTableByteCount;
    }

    public void setQueryCacheMaxResultByteCount(final Long queryCacheMaxResultByteCount) {
        _queryCacheMaxResultByteCount = queryCacheMaxResultByteCount;
    }

    public void setQueryCacheByteCount(final Long queryCacheByteCount) {
        _queryCacheByteCount = queryCacheByteCount;
    }

    public void setInnoDbBufferPoolInstanceCount(final Integer innoDbBufferPoolInstanceCount) {
        _innoDbBufferPoolInstanceCount = innoDbBufferPoolInstanceCount;
    }

    public void setInnoDbBufferPoolByteCount(final Long innoDbBufferPoolByteCount) {
        _innoDbBufferPoolByteCount = innoDbBufferPoolByteCount;
    }

    public void setInnoDbLogFileByteCount(final Long innoDbLogFileByteCount) {
        _innoDbLogFileByteCount = innoDbLogFileByteCount;
    }

    public void setInnoDbLogBufferByteCount(final Long innoDbLogBufferByteCount) {
        _innoDbLogBufferByteCount = innoDbLogBufferByteCount;
    }

    public void setInnoDbForceRecoveryLevel(final Integer innoDbForceRecoveryLevel) {
        _innoDbForceRecoveryLevel = innoDbForceRecoveryLevel;
    }

    public void enableSlowQueryLog(final String logFileName, final Long minimumQueryTime) {
        _innoDbSlowQueryLogIsEnabled = true;
        _innoDbSlowQueryLogFileName = logFileName;
        _innoDbSlowQueryLogMinimumQueryTime = minimumQueryTime;
    }

    public void disableSlowQueryLog() {
        _innoDbSlowQueryLogIsEnabled = null;
        _innoDbSlowQueryLogFileName = null;
        _innoDbSlowQueryLogMinimumQueryTime = null;
    }

    public List<String> getArguments() {
        final List<String> arguments = Util.copyList(_arguments);

        _addArgumentIfNotNull(arguments, "--max_allowed_packet", _maxAllowedPacketByteCount);
        _addArgumentIfNotNull(arguments, "--key_buffer_size", _keyBufferByteCount);
        _addArgumentIfNotNull(arguments, "--thread_stack", _threadStackNestedCallLimit);
        _addArgumentIfNotNull(arguments, "--thread_cache_size", _threadCacheThreadCount);
        _addArgumentIfNotNull(arguments, "--table_open_cache", _tableOpenCacheTableCount);
        _addArgumentIfNotNull(arguments, "--max_heap_table_size", _maxHeapTableByteCount);
        _addArgumentIfNotNull(arguments, "--query_cache_limit", _queryCacheMaxResultByteCount);
        _addArgumentIfNotNull(arguments, "--query_cache_size", _queryCacheByteCount);
        _addArgumentIfNotNull(arguments, "--innodb_buffer_pool_instances", _innoDbBufferPoolInstanceCount);
        _addArgumentIfNotNull(arguments, "--innodb_buffer_pool_size", _innoDbBufferPoolByteCount);
        _addArgumentIfNotNull(arguments, "--innodb_log_file_size", _innoDbLogFileByteCount);
        _addArgumentIfNotNull(arguments, "--innodb_log_buffer_size", _innoDbLogBufferByteCount);

        { // Slow Query Logging...
            if (_innoDbSlowQueryLogIsEnabled != null) {
                _addKeyValuePairArgument(arguments, "--slow-query-log", (_innoDbSlowQueryLogIsEnabled ? 1 : 0));
            }
            _addArgumentIfNotNull(arguments, "--slow-query-log-file", _innoDbSlowQueryLogFileName);
            _addArgumentIfNotNull(arguments, "--long-query-time", _innoDbSlowQueryLogMinimumQueryTime);
        }

        return arguments;
    }

    public List<String> getInstallationArguments() {
        final List<String> installationArguments = Util.copyList(_installationArguments);

        _addArgumentIfNotNull(installationArguments, "--innodb_force_recovery", _innoDbForceRecoveryLevel);

        return installationArguments;
    }

    protected static void _addArgumentIfNotNull(final List<String> arguments, final String argumentName, final Object value) {
        if (value != null) {
            final String stringValue = value.toString();
            _addKeyValuePairArgument(arguments, argumentName, stringValue);
        }
    }

    protected static void _addKeyValuePairArgument(final List<String> arguments, final String key, final Object value) {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(key);
        stringBuilder.append("=");
        stringBuilder.append(value);

        arguments.add(stringBuilder.toString());
    }
}
