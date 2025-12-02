#!/bin/bash
# Simple script to run TrackMate for development

cd "$(dirname "$0")"

echo "================================================"
echo "  TrackMate Development Launcher"
echo "================================================"

# Compile if needed
echo "Compiling..."
mvn compile test-compile -DskipTests -q

# Build classpath
echo "Building classpath..."
mvn dependency:build-classpath -Dmdep.outputFile=.classpath.txt -q

CP=$(cat .classpath.txt):target/classes:target/test-classes

echo "Launching TrackMate..."
echo ""

# Add JVM flags to allow reflection access (needed for Java 9+)
java --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/sun.awt=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -cp "$CP" fiji.plugin.trackmate.DevLauncher
