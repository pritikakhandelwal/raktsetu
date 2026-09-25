@echo off
rem Builds the app if needed, then starts the RaktSetu console.
cd /d "%~dp0"
set JAR=raktsetu-cli\target\raktsetu.jar
if not exist "%JAR%" call mvnw.cmd -B -q -ntp package -DskipTests
java -jar "%JAR%" %*
