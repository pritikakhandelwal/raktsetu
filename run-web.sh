#!/usr/bin/env sh
# Builds the web app if needed, then starts it at http://localhost:8080
set -e
cd "$(dirname "$0")"
JAR=raktsetu-server/target/raktsetu-server.jar
if [ ! -f "$JAR" ]; then
  ./mvnw -B -q -ntp package -DskipTests
fi
exec java -jar "$JAR" "$@"
