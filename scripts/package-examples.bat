@echo off
rem --------------------------------------------------------------------------
rem package-examples.bat
rem Builds export ZIPs for each turing-example app.
rem Each ZIP follows the Turing import format:
rem   - export.json           (SN site configuration)
rem   - *_content.json        (indexed content, if present)
rem   - app/                  (compiled SPA template)
rem
rem Output: target\examples\{name}.zip
rem --------------------------------------------------------------------------
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set EXAMPLES_DIR=%PROJECT_ROOT%\turing-marketplace
set OUTPUT_DIR=%PROJECT_ROOT%\target\examples
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

echo ==^> Packaging example site exports...

for %%D in (mythical-creatures space-missions vinyl-records) do (
    echo.
    echo --- %%D ---

    set "EXAMPLE=%EXAMPLES_DIR%\%%D"
    set "EXPORT_SRC=!EXAMPLE!\export"
    set "APP_SRC=!EXAMPLE!\app"
    set "WORK=%OUTPUT_DIR%\%%D-work"

    if not exist "!EXPORT_SRC!\export.json" (
        echo    Skipping %%D: no export\export.json found
    ) else (
        rem Create work directory
        mkdir "!WORK!"

        rem Copy export.json and content files
        copy "!EXPORT_SRC!\export.json" "!WORK!\" >nul
        for %%F in ("!EXPORT_SRC!\*_content.json") do (
            copy "%%F" "!WORK!\" >nul
        )

        rem Build the SPA app if needed
        if exist "!APP_SRC!\package.json" (
            echo    Building SPA...
            pushd "!APP_SRC!"
            call npm run compile 2>nul
            popd
        )

        rem Copy compiled SPA to app/ folder
        if exist "!APP_SRC!\dist" (
            echo    Including SPA template in app/
            mkdir "!WORK!\app"
            xcopy "!APP_SRC!\dist\*" "!WORK!\app\" /s /e /q >nul
        )

        rem Create ZIP
        echo    Creating %%D.zip
        "!PS!" -NoProfile -Command "Compress-Archive -Path '!WORK!\*' -DestinationPath '%OUTPUT_DIR%\%%D.zip'"

        rem Cleanup work dir
        rmdir /s /q "!WORK!"

        echo    Done: target\examples\%%D.zip
    )
)

echo.
echo ==^> Example packages created in target\examples\
endlocal
