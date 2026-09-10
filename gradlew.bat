@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.14"
set "GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\manual-dists\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto run

echo Gradle %GRADLE_VERSION% not found locally. Downloading wrapper distribution...
if not exist "%USERPROFILE%\.gradle\wrapper\manual-dists" mkdir "%USERPROFILE%\.gradle\wrapper\manual-dists"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%'; Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%USERPROFILE%\.gradle\wrapper\manual-dists' -Force"
if errorlevel 1 (
  echo Failed to download or extract Gradle %GRADLE_VERSION%.
  exit /b 1
)

:run
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
