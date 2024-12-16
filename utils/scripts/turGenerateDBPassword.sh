#! /bin/bash

BIN_DIR="$1"

if [ -n "$1" ]; then
    ENCRYPTED_FILE="${BIN_DIR}/db-encrypted.properties"
    echo -n Password:
    read -s password
    echo
    
    echo -n Retype Password:
    read -s retypePassword
    echo
    echo
    if [ $password = $retypePassword ]; then
        # Run Command
        touch ${ENCRYPTED_FILE}
        $1/viglet-turing.jar console encrypt --input=$password 2> ${ENCRYPTED_FILE}
        echo "Thanks, the password was successfully generated in ${ENCRYPTED_FILE}"
    else
        echo "Passwords do not match, please try again."
    fi
    echo
else
    echo "Add Turing AI Server Directory Path, for example: "
    echo "./turGenerateDBPassword.sh /appl/viglet/turing/server"
fi


