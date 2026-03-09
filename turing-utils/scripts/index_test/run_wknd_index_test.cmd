@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "BASE_URL=http://localhost:2700"
set "API_KEY="
set "SITE=wknd"
set "LOCALE=en_US"
set "SPECS_FILE=%SCRIPT_DIR%sample_wknd_field_specs.json"
set "MODE_FLAG="

if "%~1"=="" (
  echo Usage: %~nx0 --api-key KEY [OPTIONS]
  echo.
  echo Required:
  echo   --api-key KEY        Turing API key for authentication
  echo.
  echo Optional:
  echo   --base-url URL       Base URL of the Turing server (default: http://localhost:2700^)
  echo   --site NAME          Turing site name (default: wknd^)
  echo   --locale LOCALE      Locale for indexing (default: en_US^)
  echo   --specs-file PATH    Path to field specs JSON file (default: sample_wknd_field_specs.json^)
  echo   --dry-run            Validate without indexing
  echo.
  echo Example:
  echo   %~nx0 --api-key mykey123
  echo   %~nx0 --api-key mykey123 --base-url http://myserver:2700 --site mysite --locale pt_BR
  exit /b 0
)

:parse_args
if "%~1"=="" goto done_args
if /I "%~1"=="--base-url"   ( set "BASE_URL=%~2"   & shift & shift & goto parse_args )
if /I "%~1"=="--api-key"    ( set "API_KEY=%~2"    & shift & shift & goto parse_args )
if /I "%~1"=="--site"       ( set "SITE=%~2"       & shift & shift & goto parse_args )
if /I "%~1"=="--locale"     ( set "LOCALE=%~2"     & shift & shift & goto parse_args )
if /I "%~1"=="--specs-file" ( set "SPECS_FILE=%~2" & shift & shift & goto parse_args )
if /I "%~1"=="--dry-run"    ( set "MODE_FLAG=--dry-run" & shift & goto parse_args )
echo Unknown argument: %~1
exit /b 1
:done_args

if not defined API_KEY (
  echo Error: --api-key is required.
  exit /b 1
)

rem Detect whether the specs file is available
set "HAS_SPECS="
if exist "%SPECS_FILE%" set "HAS_SPECS=1"

where python >nul 2>nul
if errorlevel 1 (
  echo Python not found in PATH.
  exit /b 1
)

echo Running Turing indexing test...
echo Base URL:  %BASE_URL%
echo API Key:   %API_KEY%
echo Site:      %SITE%
echo Locale:    %LOCALE%
echo JSON:      %SCRIPT_DIR%sample_wknd_document.json
if defined HAS_SPECS (echo Specs:     %SPECS_FILE%) else (echo Specs:     [none])
echo.

if defined HAS_SPECS (
  python "%SCRIPT_DIR%turing_index_test.py" ^
    --base-url "%BASE_URL%" ^
    --header "Key: %API_KEY%" ^
    --site "%SITE%" ^
    --locale "%LOCALE%" ^
    --json-file "%SCRIPT_DIR%sample_wknd_document.json" ^
    --specs-file "%SPECS_FILE%" ^
    %MODE_FLAG%
) else (
  python "%SCRIPT_DIR%turing_index_test.py" ^
    --base-url "%BASE_URL%" ^
    --header "Key: %API_KEY%" ^
    --site "%SITE%" ^
    --locale "%LOCALE%" ^
    --json-file "%SCRIPT_DIR%sample_wknd_document.json" ^
    %MODE_FLAG%
)

set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo Indexing failed. Exit code: %EXIT_CODE%
  exit /b %EXIT_CODE%
)

echo.
echo Indexing submitted successfully.
exit /b 0
