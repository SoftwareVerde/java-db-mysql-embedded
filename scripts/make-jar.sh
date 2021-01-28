#!/bin/bash

mkdir -p out/bin

# Delete old dependencies
rm -f build/libs/libs/*

./gradlew makeJar && cp $(ls -r build/libs/*.jar | grep -v server | head -1) out/bin/. && chmod 770 out/bin/*.jar
success=$?

if [[ ! "${success}" ]]; then
    exit 1
fi

exit 0
