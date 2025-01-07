@ECHO OFF
call %~dp0env.cmd
java -Dloader.path=%~dp0libs ^
-Dturing.url=%TURING_URL% ^
-Dturing.apiKey=%TURING_API_KEY% ^
-Dturing.connector.plugin=com.viglet.turing.connector.plugin.aem.TurAemPlugin ^
-jar turing-connector.jar