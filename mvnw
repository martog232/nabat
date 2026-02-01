#!/bin/sh
# Maven Wrapper script
# Downloads and runs Maven

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_PROPERTIES_PATH="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -f "$MAVEN_WRAPPER_PROPERTIES_PATH" ]; then
    MAVEN_DOWNLOAD_URL=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES_PATH" | cut -d'=' -f2)
fi

MAVEN_DOWNLOAD_URL="${MAVEN_DOWNLOAD_URL:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip}"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven..."
    mkdir -p "$MAVEN_HOME"
    TMPFILE=$(mktemp)
    curl -fsSL "$MAVEN_DOWNLOAD_URL" -o "$TMPFILE"
    unzip -q "$TMPFILE" -d "$MAVEN_HOME/.."
    rm "$TMPFILE"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"

