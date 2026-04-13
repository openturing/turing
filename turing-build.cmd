@echo off
call mvn clean install -DskipTests -Dmaven.test.skip=true
if errorlevel 1 (   
    echo Build failed. Exiting.
    exit /b 1
)   