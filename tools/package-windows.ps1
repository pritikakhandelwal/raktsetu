# Builds the Windows app: a folder with RaktSetu.exe (the web app) and "RaktSetu Console.exe" (the course V1 console),
# each carrying its own Java runtime, zipped as dist\RaktSetu-<version>-windows.zip. Nothing needs installing to run it.
#
#   powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1 [-Version 1.1.0] [-SkipBuild]
#
# Needs a JDK 21+ (for jpackage) to build; the people running the app need nothing.
param(
    [string]$Version = "1.1.0",
    [switch]$SkipBuild
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $SkipBuild) {
    & .\mvnw.cmd -B -q -ntp package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
}

$stage = Join-Path $root "target\windows"
$jars = Join-Path $stage "input"
$out = Join-Path $stage "out"
Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $jars, $out | Out-Null
Copy-Item raktsetu-server\target\raktsetu-server.jar $jars
Copy-Item raktsetu-cli\target\raktsetu.jar $jars

# The console app as a second launcher in the same folder, sharing the runtime. jpackage puts every input jar on both
# launchers' class paths; that is harmless because both jars are built from the same code in the same run.
$console = Join-Path $stage "console.properties"
@"
main-jar=raktsetu.jar
arguments=--data raktsetu-data
win-console=true
description=RaktSetu console (course V1)
"@ | Set-Content -Encoding ascii $console

# Double-click: start the server, keep its data in the user's home folder, open the browser.
# --win-console keeps a window open that shows the address; closing it stops RaktSetu.
jpackage --type app-image --name RaktSetu --app-version $Version --vendor "Pritika Khandelwal" `
    --description "RaktSetu: expiry-aware blood redistribution" `
    --input $jars --main-jar raktsetu-server.jar --dest $out --win-console `
    --arguments "--raktsetu.open-browser=true" --arguments "--raktsetu.data-dir=~/RaktSetu/data" `
    --add-launcher "RaktSetu Console=$console" `
    --jlink-options "--strip-debug --no-man-pages --no-header-files --strip-native-commands"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Copy-Item tools\windows-readme.txt (Join-Path $out "RaktSetu\README.txt")
New-Item -ItemType Directory -Force dist | Out-Null
$zip = Join-Path $root "dist\RaktSetu-$Version-windows.zip"
Remove-Item -Force $zip -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $out "RaktSetu") -DestinationPath $zip
Write-Host "Built $zip"
