#!/bin/bash

mkdir -p out/bin

# Delete old dependencies
rm -f build/libs/libs/*

./gradlew makeServerJar copyDependencies && cp $(ls -r build/libs/*.jar | grep server | head -1) out/bin/main.jar && cp -R build/libs/libs out/bin/. && chmod 770 out/bin/main.jar
success=$?

if [[ ! "${success}" ]]; then
    exit 1
fi

exit 0
