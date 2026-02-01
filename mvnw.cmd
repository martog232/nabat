@REM Maven Wrapper script for Windows
@REM Downloads and runs Maven

@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Downloading Maven %MAVEN_VERSION%...

    if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"

    powershell -Command "& { Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%TEMP%\maven.zip' }"

    powershell -Command "& { Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force }"

    del "%TEMP%\maven.zip" 2>nul
)

"%MAVEN_HOME%\bin\mvn.cmd" %*

