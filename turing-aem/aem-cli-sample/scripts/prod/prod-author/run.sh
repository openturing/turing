#! /bin/bash
# Insper Prod - Author
if [ "$(whoami)" != "turing" ]; then
    echo "Script must be run as user: turing"
    exit 1
fi
cd $(dirname "$0") || exit
. ../env.sh
{
  ../turing-aem.sh
  curl "${SOLR_URL}/sample-author_pt_BR/update?commit=true"
  curl "${SOLR_URL}/sample-author_en_US/update?commit=true"
} >> stdout.txt 2>&1
