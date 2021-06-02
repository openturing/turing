#! /bin/bash

FILE=/opt/solr/server/solr/ready.txt     
if [ -f $FILE ]; then
   echo "File $FILE exists."
else
   echo "File $FILE does not exist."
   echo "Removing previous configuration ..."
   rm -Rf /opt/solr/server/solr/*
   echo "Copying new configuration ..."
   cp -Rf /opt/solr/turing/config/* /opt/solr/server/solr/
   touch $FILE
fi

