#! /bin/bash
if [ "$(whoami)" != "turing" ]; then
    echo "Script must be run as user: turing"
    exit 1
fi
if ps -A www |grep turing-aem.jar |grep -v grep > /dev/null;
then
  echo "has turing-aem process"
else
  echo "no turing-aem process"

cd /appl/viglet/turing/aem || exit

## INSPER
prod-author/run.sh &
prod-publish/run.sh &

fi
