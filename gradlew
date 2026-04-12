#!/bin/sh
# Gradle wrapper for Unix/Linux/macOS
# 和 gradlew.bat 一样：优先用 wrapper JAR，否则用系统 gradle

DIRNAME=$(dirname "$0")
APP_HOME=$(cd "$DIRNAME" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
else
    echo "[WARNING] gradle-wrapper.jar not found. Falling back to system gradle."
    echo "[INFO] Copy gradle-wrapper.jar from FabricMC/fabric-example-mod to fix this."
    exec gradle "$@"
fi
