package com.softwareverde.database.mysql.embedded;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

/**
 * <p>Represents MariaDB/MySQL configuration properties.</p>
 *
 * <p>Provides a number of setters that can be used to specify certain commonly set values in a type-safe manner.  In an
 * effort to ensure that the purpose of the arguments are clear, the setters do not necessarily have names that match
 * the command-line argument names.  Calling a setter with a value of <code>null</code> will have the effect of un-setting
 * that argument.  When possible, new setters should be created in lieu of calling the more generic <code>addArgument</code>
 * method, which is provided to account for the fact that it would not be practical to maintain a full up-to-date list
 * of available command-line arguments.</p>
 *
 * <p>For more information visit: https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html</p>
 */
public class MysqlDatabaseConfiguration {
    public static final Integer DEFAULT_PORT = 3306;

    protected static void _addArgumentIfNotNull(final MutableList<String> arguments, final String argumentName, final Object value) {
        if (value != null) {
            final String stringValue = value.toString();
            _addKeyValuePairArgument(arguments, argumentName, stringValue);
        }
    }

    protected static void _addKeyValuePairArgument(final MutableList<String> arguments, final String key, final Object value) {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(key);
        stringBuilder.append("=");
        stringBuilder.append(value);

        arguments.add(stringBuilder.toString());
    }

    protected final MutableList<String> _arguments;
    protected Integer _port;
    protected Long _maxConnectionCount;
    protected Long _maxAllowedPacketByteCount;
    protected Long _keyBufferByteCount;
    protected Long _threadStackNestedCallLimit;
    protected Long _threadCacheThreadCount;
    protected Long _tableOpenCacheTableCount;
    protected Long _maxHeapTableByteCount;
    protected Long _queryCacheMaxResultByteCount;
    protected Long _queryCacheByteCount;
    protected Integer _innoDbBufferPoolInstanceCount;
    protected Long _innoDbBufferPoolByteCount;
    protected Long _innoDbLogFileByteCount;
    protected Long _innoDbLogBufferByteCount;

    protected Boolean _innoDbSlowQueryLogIsEnabled;
    protected Long _innoDbSlowQueryLogMinimumQueryTime;
    protected String _innoDbSlowQueryLogFileName;

    protected Boolean _generalLogIsEnabled;
    protected String _generalLogFileName;

    // IO Capacity Params
    protected Integer _innoDbFlushLogAtTransactionCommit;
    protected String _innoDbFlushMethod; // fsync / O_DSYNC / littlesync / nosync / O_DIRECT / O_DIRECT_NO_FSYNC
    protected Long _innoDbIoCapacity;
    protected Long _innoDbIoCapacityMax;
    protected Integer _innoDbPageCleaners;
    protected Float _innoDbMaxDirtyPagesPercent; // NOTE: MySql wants values between 0.00 and 99.99
    protected Float _innoDbMaxDirtyPagesPercentLowWaterMark; // NOTE: MySql wants values between 0.00 and 99.99
    protected Integer _innoDbReadIoThreads;
    protected Integer _innoDbWriteIoThreads;
    protected Integer _innoDbLeastRecentlyUsedScanDepth; // LRU

    protected Integer _myisamSortBufferSize;

    protected Boolean _performanceSchemaEnable;

    protected MutableList<String> _getArguments() {
        final MutableList<String> arguments = new MutableList<>(_arguments);

        _addArgumentIfNotNull(arguments, "--port", _port);
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

        _addArgumentIfNotNull(arguments, "--max-connections", _maxConnectionCount);
        _addArgumentIfNotNull(arguments, "--innodb-flush-log-at-trx-commit", _innoDbFlushLogAtTransactionCommit);
        _addArgumentIfNotNull(arguments, "--innodb-flush-method", _innoDbFlushMethod);
        _addArgumentIfNotNull(arguments, "--innodb-io-capacity", _innoDbIoCapacity);
        _addArgumentIfNotNull(arguments, "--innodb-io-capacity-max", _innoDbIoCapacityMax);
        _addArgumentIfNotNull(arguments, "--innodb-page-cleaners", _innoDbPageCleaners);
        _addArgumentIfNotNull(arguments, "--innodb-max-dirty-pages-pct", _innoDbMaxDirtyPagesPercent);
        _addArgumentIfNotNull(arguments, "--innodb-max-dirty-pages-pct-lwm", _innoDbMaxDirtyPagesPercentLowWaterMark);
        _addArgumentIfNotNull(arguments, "--innodb-read-io-threads", _innoDbReadIoThreads);
        _addArgumentIfNotNull(arguments, "--innodb-write-io-threads", _innoDbWriteIoThreads);
        _addArgumentIfNotNull(arguments, "--innodb-lru-scan-depth", _innoDbLeastRecentlyUsedScanDepth);
        _addArgumentIfNotNull(arguments, "--myisam-sort-buffer-size", _myisamSortBufferSize);

        if (_performanceSchemaEnable != null) {
            _addArgumentIfNotNull(arguments, "--performance-schema", (_performanceSchemaEnable ? "ON" : "OFF"));
        }

        { // Slow Query Logging...
            if (_innoDbSlowQueryLogIsEnabled != null) {
                _addKeyValuePairArgument(arguments, "--slow-query-log", (_innoDbSlowQueryLogIsEnabled ? 1 : 0));
            }
            _addArgumentIfNotNull(arguments, "--slow-query-log-file", _innoDbSlowQueryLogFileName);
            _addArgumentIfNotNull(arguments, "--long-query-time", _innoDbSlowQueryLogMinimumQueryTime);
        }

        { // General Log...
            if (_generalLogIsEnabled != null) {
                _addKeyValuePairArgument(arguments, "--general-log", (_generalLogIsEnabled ? 1 : 0));
            }
            _addKeyValuePairArgument(arguments, "--general-log-file", _generalLogFileName);
        }

        return arguments;
    }

    public MysqlDatabaseConfiguration() {
        _arguments = new MutableList<>();
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

    public void setPort(final Integer port) {
        _port = port;
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

    public void enableGeneralQueryLog(final String logFileName) {
        _generalLogIsEnabled = true;
        _generalLogFileName = logFileName;
    }

    public void disableGeneralQueryLog() {
        _generalLogIsEnabled = null;
        _generalLogFileName = null;
    }

    public void setInnoDbIoCapacity(final Long innoDbIoCapacity) {
        _innoDbIoCapacity = innoDbIoCapacity;
    }

    public void setMaxConnectionCount(final Long maxConnectionCount) {
        _maxConnectionCount = maxConnectionCount;
    }

    public void setInnoDbFlushLogAtTransactionCommit(final Integer innoDbFlushLogAtTransactionCommit) {
        _innoDbFlushLogAtTransactionCommit = innoDbFlushLogAtTransactionCommit;
    }

    public void setInnoDbFlushMethod(final String innoDbFlushMethod) {
        _innoDbFlushMethod = innoDbFlushMethod;
    }

    public void setInnoDbIoCapacityMax(final Long innoDbIoCapacityMax) {
        _innoDbIoCapacityMax = innoDbIoCapacityMax;
    }

    public void setInnoDbPageCleaners(final Integer innoDbPageCleaners) {
        _innoDbPageCleaners = innoDbPageCleaners;
    }

    public void setInnoDbMaxDirtyPagesPercent(final Float innoDbMaxDirtyPagesPercent) {
        _innoDbMaxDirtyPagesPercent = innoDbMaxDirtyPagesPercent;
    }

    public void setInnoDbMaxDirtyPagesPercentLowWaterMark(final Float innoDbMaxDirtyPagesPercentLowWaterMark) {
        _innoDbMaxDirtyPagesPercentLowWaterMark = innoDbMaxDirtyPagesPercentLowWaterMark;
    }

    public void setInnoDbReadIoThreads(final Integer innoDbReadIoThreads) {
        _innoDbReadIoThreads = innoDbReadIoThreads;
    }

    public void setInnoDbWriteIoThreads(final Integer innoDbWriteIoThreads) {
        _innoDbWriteIoThreads = innoDbWriteIoThreads;
    }

    public void setInnoDbLeastRecentlyUsedScanDepth(final Integer innoDbLeastRecentlyUsedScanDepth) {
        _innoDbLeastRecentlyUsedScanDepth = innoDbLeastRecentlyUsedScanDepth;
    }

    public void setMyisamSortBufferSize(final Integer myisamSortBufferSize) {
        _myisamSortBufferSize = myisamSortBufferSize;
    }

    public void setPerformanceSchemaEnable(final Boolean performanceSchemaEnable) {
        _performanceSchemaEnable = performanceSchemaEnable;
    }

    public List<String> getCommandlineArguments() {
        return _getArguments();
    }

    public String getDefaultsFile() {
        final String newline = System.lineSeparator();

        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[mysqld]");
        stringBuilder.append(newline);

        final List<String> arguments = _getArguments();
        for (final String argument : arguments) {
            String appliedArgument = argument;

            if (appliedArgument.startsWith("--")) {
                appliedArgument = appliedArgument.substring(2);
            }

            stringBuilder.append(appliedArgument);
            stringBuilder.append(newline);
        }

        return stringBuilder.toString();
    }
}
