@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "BASE_URL=http://localhost:2700"
if not "%~1"=="" set "BASE_URL=%~1"

set "MODE_FLAG="
if /I "%~2"=="--dry-run" set "MODE_FLAG=--dry-run"

rem Optional: path to field specs JSON (defaults to sample_wknd_field_specs.json)
set "SPECS_FILE=%SCRIPT_DIR%sample_wknd_field_specs.json"
if not "%~3"=="" set "SPECS_FILE=%~3"

rem Detect whether the specs file is available
set "HAS_SPECS="
if exist "%SPECS_FILE%" set "HAS_SPECS=1"

where python >nul 2>nul
if errorlevel 1 (
  echo Python nao encontrado no PATH.
  exit /b 1
)

echo Running Turing indexing test...
echo Base URL: %BASE_URL%
echo Site: wknd
echo Locale: en_US
echo JSON: %SCRIPT_DIR%sample_wknd_document.json
if defined HAS_SPECS (echo Specs: %SPECS_FILE%) else (echo Specs: [none])
echo.

if defined HAS_SPECS (
  python "%SCRIPT_DIR%turing_index_test.py" ^
    --base-url "%BASE_URL%" ^
    --header "Key: 09e069003cd047fc918e62be8" ^
    --site wknd ^
    --locale en_US ^
    --json-file "%SCRIPT_DIR%sample_wknd_document.json" ^
    --specs-file "%SPECS_FILE%" ^
    %MODE_FLAG%
) else (
  python "%SCRIPT_DIR%turing_index_test.py" ^
    --base-url "%BASE_URL%" ^
    --header "Key: 09e069003cd047fc918e62be8" ^
    --site wknd ^
    --locale en_US ^
    --json-file "%SCRIPT_DIR%sample_wknd_document.json" ^
    %MODE_FLAG%
)

set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo Falha na indexacao. Exit code: %EXIT_CODE%
  exit /b %EXIT_CODE%
)

echo.
echo Indexacao enviada com sucesso.
exit /b 0
