package com.softwareverde.database.mysql.embedded;

import com.softwareverde.util.SystemUtil;

public enum OperatingSystemType {
    LINUX("linux"),
    MAC_OSX("osx"),
    WINDOWS("windows");

    public static OperatingSystemType getOperatingSystemType() {
        if (SystemUtil.isWindowsOperatingSystem()) {
            return WINDOWS;
        }

        if (SystemUtil.isMacOperatingSystem()) {
            return MAC_OSX;
        }

        if (SystemUtil.isLinuxOperatingSystem()) {
            return LINUX;
        }

        return null;
    }

    protected final String _value;

    OperatingSystemType(final String value) {
        _value = value;
    }

    @Override
    public String toString() {
        return _value;
    }
}
