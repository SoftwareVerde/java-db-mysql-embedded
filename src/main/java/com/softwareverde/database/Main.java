package com.softwareverde.database;

import com.softwareverde.logging.LineNumberAnnotatedLog;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

import java.io.File;

public class Main {
    public static void main(final String[] parameters) {
        Logger.setLog(LineNumberAnnotatedLog.getInstance());
        Logger.setLogLevel(LogLevel.ON);

        try {
            final File installationDirectory = new File("db");
            final MariaDb mariaDb = new MariaDb(MariaDb.OperatingSystemType.getOperatingSystemType(), installationDirectory, new File("data"));
            mariaDb.install("password");
            mariaDb.start();

            final Thread thread = Thread.currentThread();
            while (! thread.isInterrupted()) {
                Thread.sleep(1000L);
            }

            mariaDb.stop();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }
}
