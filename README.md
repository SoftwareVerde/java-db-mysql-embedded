# Java Embedded MySQL Database

## v3.0.0

Inspired by MariaDB4j, this package provides a framework for prebuilt MariaDB binaries for
Windows, Linux, and OSX.

This framework improves upon MariaDB4j by having fewer dependencies, using a modern build process, a modern
version of MariaDB4j, and ensures the database process is killed upon crash (when used with the prebuilt
binaries/scripts provided below). This implementation is a rewrite and does not depend on MariaDB4j in any capacity.

Prebuilt binaries may be included via the following dependencies, and are not mutually-exclusive:

    implementation  group: 'com.github.softwareverde',  name: 'java-mariadb-osx',       version: 'v10.5.8'
    implementation  group: 'com.github.softwareverde',  name: 'java-mariadb-windows',   version: 'v10.5.8'
    implementation  group: 'com.github.softwareverde',  name: 'java-mariadb-linux',     version: 'v10.5.8'

Including multiple versions of prebuilt binaries for any one OS may result in undefined behavior.

If you choose to use your own compiled binaries, the resource jar must provide the following:

1. A manifest file located within `src/main/resources/mysql/<OS>/manifest`
    - The manifest format must include the resource path and an executable flag (designated via `x`) if the extracted
      file should be executable.
    - Any files not within the manifest will not be extracted from the resource jar.
2. A `init.sh` script for the Linux/OSX environments that initializes the data directories, located within
   `src/main/resources/mysql/<OS>/.`
    - The `init.sh` script will receive the data directory as its first parameter.
3. A `run.sh` script for Linux/OSX and a `run.bat` script for Windows environments, located within
   `src/main/resources/mysql/<OS>/.`
   - The run scripts do not receive any parameters, but may rely on the `.datadir` helper file located within
     `src/main/resources/mysql/<OS>/.` to load the location of the database data files.
   - Upon termination, the run script shall receive the string "exit" followed by a system-specific newline character
    to request termination, followed with a SIGINT signal after the specified timeout.  Upon the JVM crashing, the
     script is immediately terminated with no notification.

## v2.x.x

The v1-v2 series are an API wrapper around MariaDB4j.