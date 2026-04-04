@echo off
rem --------------------------------------------------------------------------
rem package-dist.bat
rem Builds Turing ES from source and creates distribution packages:
rem   - turing-install.zip   (portable, works on Linux and Windows)
rem   - turing-install.exe   (Windows installer via NSIS, if available)
rem
rem Usage:  scripts\package-dist.bat [--skip-build]
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set SKIP_BUILD=false
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

:parse_args
if "%~1"=="" goto end_args
if /i "%~1"=="--skip-build" (
    set SKIP_BUILD=true
    shift
    goto parse_args
)
echo Unknown option: %~1
exit /b 1
:end_args

pushd "%PROJECT_ROOT%"

rem --------------- Build ---------------
if "%SKIP_BUILD%"=="true" goto :skip_build
echo ==^> Building Turing ES...
where mvn >nul 2>&1
if %errorlevel%==0 (
    call mvn clean install -DskipTests -Dgpg.skip=true -Dturing.open-browser=false
) else (
    call mvnw.cmd clean install -DskipTests -Dgpg.skip=true -Dturing.open-browser=false
)
if errorlevel 1 (
    echo Build failed.
    popd
    exit /b 1
)
:skip_build

rem =====================================================================
rem  Shared: copy artifacts into a staging area
rem =====================================================================
echo ==^> Staging artifacts...

set DIST_NAME=turing-install
set STAGE=%PROJECT_ROOT%\target\dist-stage\%DIST_NAME%
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\server"
mkdir "%STAGE%\utils"
mkdir "%STAGE%\bin"

rem Server JAR
copy "turing-app\target\viglet-turing.jar" "%STAGE%\server\" >nul

rem Utils (extract from turing-utils.zip)
if exist "turing-utils\target\turing-utils.zip" (
    "!PS!" -NoProfile -Command "Expand-Archive -Path 'turing-utils\target\turing-utils.zip' -DestinationPath '%STAGE%\utils' -Force"
)

rem Scripts
copy "%SCRIPT_DIR%bin\turing.sh"            "%STAGE%\bin\" >nul
copy "%SCRIPT_DIR%bin\turing.bat"           "%STAGE%\bin\" >nul
rem Config and docs
copy "%SCRIPT_DIR%config\viglet-turing.properties" "%STAGE%\server\" >nul
copy "%SCRIPT_DIR%config\README.txt" "%STAGE%\" >nul

rem Example exports
echo ==^> Packaging example exports...
call "%SCRIPT_DIR%package-examples.bat"
set EXAMPLES_OUT=%PROJECT_ROOT%\target\examples
mkdir "%STAGE%\examples" 2>nul
if exist "%EXAMPLES_OUT%\mythical-creatures.zip" copy "%EXAMPLES_OUT%\mythical-creatures.zip" "%STAGE%\examples\" >nul
if exist "%EXAMPLES_OUT%\space-missions.zip"     copy "%EXAMPLES_OUT%\space-missions.zip"     "%STAGE%\examples\" >nul
if exist "%EXAMPLES_OUT%\vinyl-records.zip"       copy "%EXAMPLES_OUT%\vinyl-records.zip"       "%STAGE%\examples\" >nul

rem =====================================================================
rem  1. Zip
rem =====================================================================
set ZIP_OUTPUT=%PROJECT_ROOT%\target\%DIST_NAME%.zip
echo ==^> Creating zip: %ZIP_OUTPUT%
if exist "%ZIP_OUTPUT%" del "%ZIP_OUTPUT%"

set STAGE_PARENT=%PROJECT_ROOT%\target\dist-stage
pushd "%STAGE_PARENT%"
"!PS!" -NoProfile -Command "Compress-Archive -Path '%DIST_NAME%' -DestinationPath '%ZIP_OUTPUT%'"
popd

rem =====================================================================
rem  2. Windows Installer (NSIS)
rem =====================================================================
set MAKENSIS=

where makensis >nul 2>&1
if %errorlevel%==0 (
    set MAKENSIS=makensis
    goto :build_nsis
)

if exist "C:\Program Files (x86)\NSIS\makensis.exe" (
    set "MAKENSIS=C:\Program Files (x86)\NSIS\makensis.exe"
    goto :build_nsis
)

if exist "C:\Program Files\NSIS\makensis.exe" (
    set "MAKENSIS=C:\Program Files\NSIS\makensis.exe"
    goto :build_nsis
)

echo ==^> Skipping Windows installer (NSIS not found)
goto :done

:build_nsis
echo ==^> Creating Windows installer...
if not exist "%PROJECT_ROOT%\target" mkdir "%PROJECT_ROOT%\target"
"%MAKENSIS%" /V2 /DOUTDIR="%PROJECT_ROOT%\target" /DSTAGE="%STAGE%" "%SCRIPT_DIR%installer\turing.nsi"
if errorlevel 1 (
    echo Installer build failed.
) else (
    echo    target\%DIST_NAME%.exe
)

:done
echo.
echo ==^> All done. Outputs in target\

popd
endlocal
