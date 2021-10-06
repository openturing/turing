
CORE="converse"

#curl -X GET "http://${1}:8983/solr/admin/configs?action=CREATE&name=${CORE}&baseConfigSet=_default&configSetProp.immutable=false"

curl -X GET "http://${1}:8983/solr/admin/cores?action=CREATE&name=${CORE}&config=solrconfig.xml&dataDir=data&instanceDir=${CORE}&configSet=_default"

INPUT=./converse-fields.csv
OLDIFS=$IFS
IFS=','
[ ! -f $INPUT ] && {
    echo "$INPUT file not found"
    exit 99
}

{
    # skip header
    read
    while read name type multiValued indexed stored copyTo; do
        echo "Name: $name"
        echo "Type: $type"
        echo "MultiValued: $multiValued"
        echo "Indexed: $indexed"
        echo "Stored: $stored"
        echo "CopyTo: $copyTo"
        
        
        curl -X POST -H 'Content-type:application/json' --data-binary '{
          "add-field":{
          "name":"'"$name"'",
          "type":"'"$type"'",
          "multiValued": "'"$multiValued"'",
          "indexed": "'"$indexed"'",
          "stored":"'"$stored"'" }
        }' http://$1:8983/solr/$CORE/schema

        if [ "$copyTo" = "true" ]
        then {     
            curl -X POST -H 'Content-type:application/json' --data-binary '{
                "add-copy-field":{
                "source":"'"$name"'",
                "dest": "_text_" }
            }' http://$1:8983/solr/$CORE/schema
        }
        fi
    done    
} <$INPUT
IFS=$OLDIFS
