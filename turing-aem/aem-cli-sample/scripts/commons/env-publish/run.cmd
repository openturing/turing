@ECHO OFF
CALL ..\env.cmd
SET PROPERTIES_FILE=%~dp0turing-aem.properties
CALL ..\turing-aem.cmd --delivered %*
