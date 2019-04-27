#! /bin/bash
BIN_DIR=`dirname $0`
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
	password_encrypted=`build/libs/viglet-turing.jar console encrypt --input=$password`
	touch ${ENCRYPTED_FILE}
	echo "${password_encrypted}" > ${ENCRYPTED_FILE}
	echo "Thanks, the password was successfully generated in ${ENCRYPTED_FILE}" 
else 
	echo "Passwords do not match, please try again."
fi
echo


