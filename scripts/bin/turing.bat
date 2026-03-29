@echo off
rem --------------------------------------------------------------------------
rem Start Viglet Turing ES.
rem
rem Usage:  bin\turing.bat [JVM options...]
rem
rem Examples:
rem   bin\turing.bat
rem   bin\turing.bat -Xmx2g
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BASE_DIR=%SCRIPT_DIR%..
set JAR=%BASE_DIR%\server\viglet-turing.jar
set PROPS=%BASE_DIR%\server\viglet-turing.properties

rem Resolve Java executable
set JAVA_CMD=java
if exist "%SCRIPT_DIR%java-home.conf" (
    for /f "usebackq tokens=*" %%j in ("%SCRIPT_DIR%java-home.conf") do set "DUMONT_JAVA_HOME=%%j"
    if exist "!DUMONT_JAVA_HOME!\bin\java.exe" (
        set "JAVA_CMD=!DUMONT_JAVA_HOME!\bin\java.exe"
    )
)

if not exist "%JAR%" (
    echo Error: %JAR% not found.
    exit /b 1
)

set SPRING_CONFIG=
if exist "%PROPS%" (
    set "SPRING_CONFIG=--spring.config.additional-location=file:%PROPS%"
)

echo Starting Viglet Turing ES...
echo   Java:   !JAVA_CMD!
echo   JAR:    %JAR%
if exist "%PROPS%" echo   Config: %PROPS%
echo.

set "DATA_DIR=%LOCALAPPDATA%\Viglet\Turing"
if not exist "!DATA_DIR!" mkdir "!DATA_DIR!"
cd /d "!DATA_DIR!"
"!JAVA_CMD!" -Xmx1g -Xms1g %* -Dspring.output.ansi.enabled=never -jar "%JAR%" "!SPRING_CONFIG!"

endlocal
