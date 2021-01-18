package com.softwareverde.database;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.SystemUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public class MariaDb {
    public enum OperatingSystemType {
        LINUX("linux"),
        MAC_OSX("osx"),
        WINDOWS("windows");

        protected final String _value;

        OperatingSystemType(final String value) {
            _value = value;
        }

        @Override
        public String toString() {
            return _value;
        }

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
    }

    protected static File copyFile(final InputStream sourceStream, final String destinationFilename) {
        try {
            final Path destinationPath = Paths.get(destinationFilename);
            final File file = new File(destinationFilename);
            final boolean directoryWasCreatedSuccessfully = file.mkdirs();
            if (! directoryWasCreatedSuccessfully) { return null; }

            Files.copy(sourceStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
        catch (final IOException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    protected final OperatingSystemType _operatingSystemType;
    protected final File _installDirectory;
    protected final File _dataDirectory;

    protected Process _process;

    public MariaDb(final OperatingSystemType operatingSystemType, final File installDirectory, final File dataDirectory) {
        _operatingSystemType = operatingSystemType;
        _installDirectory = installDirectory;
        _dataDirectory = dataDirectory;
    }

    public void install(final String rootPassword) throws Exception {
        final String resourcePrefix = "/mysql/" + _operatingSystemType + "/";
        final String manifest = IoUtil.getResource(resourcePrefix + "manifest");
        for (final String manifestEntry : manifest.split("\n")) {

            final String flags;
            final String resource;
            {
                final int spaceIndex = manifestEntry.lastIndexOf(' ');
                if (spaceIndex < 0) {
                    resource = manifestEntry;
                    flags = "";
                }
                else {
                    resource = manifestEntry.substring(0, spaceIndex);
                    flags = manifestEntry.substring(spaceIndex + 1);
                }
            }

            final String destination = (_installDirectory.getPath() + resource.substring(resourcePrefix.length() - 1));
            final InputStream inputStream = IoUtil.getResourceAsStream(resource);
            Logger.info("Copying: " + resource + " to " + destination);
            final File copiedFile = MariaDb.copyFile(inputStream, destination);
            final boolean copyWasSuccessful = (copiedFile != null);
            if (! copyWasSuccessful) {
                throw new RuntimeException("Unable to copy resource: " + resource);
            }

            if (flags.contains("x")) {
                final boolean flagSetSuccessfully = copiedFile.setExecutable(true, true);
                if (! flagSetSuccessfully) {
                    throw new RuntimeException("Unable to set file flags: " + resource);
                }
            }
        }

        final boolean makeDataDirectoryWasSuccessful = _dataDirectory.mkdirs();
        if (! makeDataDirectoryWasSuccessful) {
            throw new RuntimeException("Unable to create data directory: " + _dataDirectory.getAbsolutePath());
        }

        final String command;
        {
            final File file = new File(_installDirectory.getPath() + "/init.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }
            command = (file.getPath() + " " + _dataDirectory.getPath());
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        Process process = null;
        try {
            process = runtime.exec(command);

            try (
                final InputStream inputStream = process.getInputStream();
                final OutputStream outputStream = process.getOutputStream();
                final BufferedReader processOutput = new BufferedReader(new InputStreamReader(inputStream))
            ) {
                outputStream.write(rootPassword.getBytes(StandardCharsets.UTF_8));
                outputStream.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String line;
                            while ((line = processOutput.readLine()) != null) {
                                Logger.debug(line);
                            }
                        }
                        catch (final Exception exception) { }
                    }
                })).start();

                final boolean initWasSuccessful = process.waitFor(30, TimeUnit.SECONDS);
                if (! initWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init failed after timeout.");
                }

                final boolean resultCodeWasSuccessful = (process.exitValue() == 0);
                if (! resultCodeWasSuccessful) {
                    throw new RuntimeException("Unable to initialize database. Init script failed.");
                }
            }
        }
        finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    public void start() throws Exception {
        final String command;
        {
            final File file = new File(_installDirectory.getPath() + "/run.sh");
            if (! (file.isFile() && file.canExecute())) {
                throw new RuntimeException("Unable to initialize database. Init script not found.");
            }
            command = (file.getPath() + " " + _dataDirectory.getPath());
        }
        final Runtime runtime = Runtime.getRuntime();
        Logger.info("Exec: " + command);
        _process = runtime.exec(command);

        final InputStream inputStream = _process.getInputStream();
        final BufferedReader processOutput = new BufferedReader(new InputStreamReader(inputStream));
        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = processOutput.readLine()) != null) {
                        Logger.debug(line);
                    }
                }
                catch (final Exception exception) { }
            }
        })).start();
    }

    public void stop() throws Exception {
        _process.destroy();

        final boolean initWasSuccessful = _process.waitFor(30, TimeUnit.SECONDS);
        if (! initWasSuccessful) {
            throw new RuntimeException("Unable to stop database. Shutdown failed after timeout.");
        }
    }
}
