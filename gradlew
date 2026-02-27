#!/usr/bin/env sh
set -eu

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

JAVACMD="${JAVA_HOME:-}/bin/java"
if [ ! -x "$JAVACMD" ]; then
  JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
  -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
