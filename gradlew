#!/usr/bin/env bash
# Gradle wrapper script

# Resolve app home
APP_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the command line
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine the Java command to use
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
