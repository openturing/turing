#! /bin/bash

solr_host=$1

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field-type" : {
     "name":"text_spellcheck",
     "class":"solr.TextField",
     "positionIncrementGap":"100",
     "analyzer" : {
        "charFilters":[{
           "class":"solr.HTMLStripCharFilterFactory"}],
        "tokenizer":{
           "class":"solr.StandardTokenizerFactory" },
        "filters":[{
           "class":"solr.LowerCaseFilterFactory"},
           {
           "class":"solr.SynonymFilterFactory",
           "expand:"true",
           "ignoreCase:"true",
           "synonyms":"synonyms.txt"},
           {
           "class":"solr.SynonymFilterFactory",
           "expand:"true",
           "ignoreCase:"true",
           "synonyms":"synonyms_spell.txt"},
           {
           "class":"solr.StopFilterFactory",
           "words:"lang/stopwords_pt.txt",
           "ignoreCase:"true"},
           {
           "class":"solr.StandardFilterFactory"},
           {
           "class":"solr.RemoveDuplicatesTokenFilterFactory"}]}}
}' $1/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field-type" : {
     "name":"text_suggest",
     "class":"solr.TextField",
     "positionIncrementGap":"100",
     "analyzer" : {
        "charFilters":[{
           "class":"solr.HTMLStripCharFilterFactory"}],
        "tokenizer":{
           "class":"solr.StandardTokenizerFactory" },
        "filters":[{
           "class":"solr.LowerCaseFilterFactory"},
           {
           "class":"solr.StandardFilterFactory"},
           {
           "class":"solr.RemoveDuplicatesTokenFilterFactory"
           },
           {
           "class":"solr.ShingleFilterFactory",
            "outputUnigrams":"true",
            "maxShingleSize":"10",
            "tokenSeparator":" "}]}}
}' $1/schema

INPUT=turing_fields.csv
OLDIFS=$IFS
IFS=','
[ ! -f $INPUT ] && {
    echo "$INPUT file not found"
    exit 99
}

{
    # skip header
    read
    while read field type multiValued indexed stored; do
        curl -X POST -H 'Content-type:application/json' --data-binary '{
            "add-field":{
            "name":"$field",
            "type":"$type",
            "multiValued": $multiValued,
            "indexed":$indexed,
            "stored":$stored }
        }' $1/schema
        
        curl -X POST -H 'Content-type:application/json' --data-binary '{
            "add-copy-field":{
            "source":"$field",
            "dest":[ "_text_" ]}
        }' $1/schema
    done
    
} <$INPUT
IFS=$OLDIFS