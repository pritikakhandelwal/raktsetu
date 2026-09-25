#!/usr/bin/env sh
# Builds the app if needed, then starts the RaktSetu console.
set -e
cd "$(dirname "$0")"
JAR=raktsetu-cli/target/raktsetu.jar
if [ ! -f "$JAR" ]; then
  ./mvnw -B -q -ntp package -DskipTests
fi
exec java -jar "$JAR" "$@"
