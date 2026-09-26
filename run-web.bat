@echo off
rem Builds the web app if needed, then starts it at http://localhost:8080
cd /d "%~dp0"
set JAR=raktsetu-server\target\raktsetu-server.jar
if not exist "%JAR%" call mvnw.cmd -B -q -ntp package -DskipTests
java -jar "%JAR%" %*
