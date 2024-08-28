@ECHO OFF
%JAVA_BIN% -classpath ".;%~dp0libs\db-sample.jar;%~dp0libs\turing-db.jar" com.viglet.turing.connector.db.TurDbImportTool %*
