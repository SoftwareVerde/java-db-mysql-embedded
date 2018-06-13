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
    private Long _queryCacheResultByteCountLimit;
    private Long _queryCacheByteCount;
    private Long _innoDbBufferPoolByteCount;
    private Long _innoDbLogFileByteCount;

    public DatabaseCommandLineArguments() {
        _arguments = new ArrayList<>();
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

    public void setTableCache(final Long tableCache) {
        _tableOpenCacheTableCount = tableCache;
    }

    public void setMaxHeapTableByteCount(final Long maxHeapTableByteCount) {
        _maxHeapTableByteCount = maxHeapTableByteCount;
    }

    public void setQueryCacheResultByteCountLimit(final Long queryCacheResultByteCountLimit) {
        _queryCacheResultByteCountLimit = queryCacheResultByteCountLimit;
    }

    public void setQueryCacheByteCount(final Long queryCacheByteCount) {
        _queryCacheByteCount = queryCacheByteCount;
    }

    public void setInnoDbBufferPoolByteCount(final Long innoDbBufferPoolByteCount) {
        _innoDbBufferPoolByteCount = innoDbBufferPoolByteCount;
    }

    public void setInnoDbLogFileByteCount(final Long innoDbLogFileByteCount) {
        _innoDbLogFileByteCount = innoDbLogFileByteCount;
    }

    public List<String> getArguments() {
        final List<String> arguments = Util.copyList(_arguments);

        _addArgumentIfNotNull(arguments, "--max_allowed_packet", _maxAllowedPacketByteCount);
        _addArgumentIfNotNull(arguments, "--key_buffer_size", _keyBufferByteCount);
        _addArgumentIfNotNull(arguments, "--thread_stack", _threadStackNestedCallLimit);
        _addArgumentIfNotNull(arguments, "--thread_cache_size", _threadCacheThreadCount);
        _addArgumentIfNotNull(arguments, "--table_open_cache", _tableOpenCacheTableCount);
        _addArgumentIfNotNull(arguments, "--max_heap_table_size", _maxHeapTableByteCount);
        _addArgumentIfNotNull(arguments, "--query_cache_limit", _queryCacheResultByteCountLimit);
        _addArgumentIfNotNull(arguments, "--query_cache_size", _queryCacheByteCount);
        _addArgumentIfNotNull(arguments, "--innodb_buffer_pool_size", _innoDbBufferPoolByteCount);
        _addArgumentIfNotNull(arguments, "--innodb_log_file_size", _innoDbLogFileByteCount);

        return arguments;
    }

    protected static void _addArgumentIfNotNull(final List<String> arguments, final String argumentName, final Object value) {
        if (value != null) {
            final String stringValue = value.toString();
            _addKeyValuePairArgument(arguments, argumentName, stringValue);
        }
    }

    protected static void _addKeyValuePairArgument(final List<String> arguments, final String key, final String value) {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(key);
        stringBuilder.append("=");
        stringBuilder.append(value);

        arguments.add(stringBuilder.toString());
    }
}
