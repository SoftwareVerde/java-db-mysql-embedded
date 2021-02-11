package com.softwareverde.database.mysql.embedded;

import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcessOutputLogger extends Thread {
    public ProcessOutputLogger(final InputStream inputStream, final LoggerInstance loggerInstance) {
        super(new Runnable() {
            @Override
            public void run() {
                try (
                    final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    final BufferedReader contentReader = new BufferedReader(inputStreamReader)
                ) {
                    String line;
                    while ((line = contentReader.readLine()) != null) {
                        loggerInstance.trace(line);
                    }
                }
                catch (final Exception exception) { }
                finally {
                    loggerInstance.trace("ProcessOutputLogger exiting.");
                }
            }
        });

        this.setName("Process Output Logger");
        this.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
    }

}
