@echo off
setlocal

for %%A in (%*) do (
    if /i "%%A"=="help" goto :help
    if /i "%%A"=="-h" goto :help
    if /i "%%A"=="--help" goto :help
    if /i "%%A"=="/?" goto :help
)

set LOGGING_CONFIG=classpath:logback-spring.xml
set MONGO_ENABLED=false
set MINIO_ENABLED=false
set GIT_ENABLED=false
set STORAGE_TYPE=FILESYSTEM
set LOGGING_ENGINE=none
set LOG_LEVEL=INFO

rem Master key used by TurSecretCryptoService to encrypt/decrypt provider API keys.
rem The default active profile is "production", which fails fast when this is blank
rem (TurCryptoKeyMaterial / T639). Respect an existing TURING_AI_CRYPTO_KEY from the
rem environment; otherwise fall back to a stable LOCAL DEV key. Do NOT reuse this
rem value in a real production deployment -- set a strong secret via the environment
rem there and rotate any secrets encrypted with this local key (see
rem docs/operations/crypto-key-rotation.md). Must not be a rejected sentinel.
if not defined TURING_AI_CRYPTO_KEY set TURING_AI_CRYPTO_KEY=turing-local-dev-2026-crypto-master-key

for %%A in (%*) do (
    if /i "%%A"=="mongo" (
        set MONGO_ENABLED=true
        set LOGGING_ENGINE=mongodb
    )
    if /i "%%A"=="minio" set STORAGE_TYPE=MINIO
    if /i "%%A"=="git" set GIT_ENABLED=true
    if /i "%%A"=="debug" set LOG_LEVEL=DEBUG
)

if /i "%MONGO_ENABLED%"=="true" set LOGGING_CONFIG=classpath:logback-spring-mongo.xml

echo Starting Turing...
echo   MongoDB: %MONGO_ENABLED%
echo   Log level ^(com.viglet^): %LOG_LEVEL%
echo   Storage: %STORAGE_TYPE%
echo   Logging Engine: %LOGGING_ENGINE%
echo   Crypto key: configured ^(TURING_AI_CRYPTO_KEY^)
echo.

call mvn spring-boot:run -pl turing-app -Dskip.npm -DskipTests -Dmaven.test.skip=true ^
"-Dspring-boot.run.jvmArguments=-Xms1g -Xmx2g -XX:ReservedCodeCacheSize=256m" ^
"^
-Dspring-boot.run.arguments=--spring.h2.console.enabled=true ^
--turing.storage.type=%STORAGE_TYPE% ^
--logging.config=%LOGGING_CONFIG% ^
--turing.logging.engine=%LOGGING_ENGINE% ^
--turing.mongodb.enabled=%MONGO_ENABLED% ^
--turing.git.server=%GIT_ENABLED% ^
--logging.level.com.viglet=%LOG_LEVEL% ^
"
goto :eof

:help
echo.
echo Usage: turing-spring.cmd [options]
echo.
echo Options can be combined in any order:
echo   ^(none^)    Start with defaults ^(no MongoDB, no MinIO, INFO log level^)
echo   mongo     Enable MongoDB logging and persistence
echo   minio     Enable MinIO logging and persistence
echo   git       Enable Git integration
echo   debug     Set com.viglet log level to DEBUG
echo   help      Show this help message
echo.
echo Examples:
echo   turing-spring.cmd                 Default settings
echo   turing-spring.cmd mongo           With MongoDB
echo   turing-spring.cmd minio           With MinIO
echo   turing-spring.cmd debug           With DEBUG logging
echo   turing-spring.cmd mongo debug     With MongoDB and DEBUG logging
echo   turing-spring.cmd git             With Git integration
echo.
