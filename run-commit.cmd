@echo off
setlocal

set SCRIPT_DIR=%~dp0

if "%OPENAI_API_KEY%"=="" (
    echo Error: OPENAI_API_KEY is not set.
    exit /b 2
)

git add *
if errorlevel 1 (
    echo git add failed.
    exit /b 1
)

python "%SCRIPT_DIR%scripts\ai_commit.py" %*

endlocal
