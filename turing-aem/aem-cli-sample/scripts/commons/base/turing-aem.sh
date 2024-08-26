#! /bin/bash
if [ "$(whoami)" != "turing" ]; then
    echo "Script must be run as user: turing"
    exit 1
fi

$JAVA_BIN -classpath ".:../libs/aem-cli-sample.jar:../libs/turing-aem.jar" \
com.viglet.turing.connector.aem.indexer.TurAemIndexerTool --property "$PROPERTIES_FILE" "$@"

