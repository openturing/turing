#! /bin/bash
#./gradlew build shadowJar
java -Dlog4j.configurationFile=log4j2.properties -cp .:build/libs/turing-filesystem-fat-jar.jar com.viglet.turing.tool.filesystem.TurFSImportTool "$@"

