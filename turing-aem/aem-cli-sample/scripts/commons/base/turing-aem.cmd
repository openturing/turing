@ECHO OFF
%JAVA_BIN% -classpath ".;%~dp0libs\aem-cli-sample.jar;%~dp0libs\turing-aem.jar" com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool --property %PROPERTIES_FILE% %*