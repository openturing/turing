
CORE="turing"

#curl -X GET "http://${1}:8983/solr/admin/configs?action=CREATE&name=${CORE}&baseConfigSet=_default&configSetProp.immutable=false"

curl -X GET "http://${1}:8983/solr/admin/cores?action=CREATE&name=${CORE}&config=solrconfig.xml&dataDir=data&instanceDir=${CORE}&configSet=_default"

INPUT=./sn-fields.csv
OLDIFS=$IFS
IFS=','
[ ! -f $INPUT ] && {
    echo "$INPUT file not found"
    exit 99
}

{
    # skip header
    read
    while read -r name type multiValued indexed stored copyTo; do
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
        }' http://$1:8983/solr/turing/schema
        
        if [  "`echo $copyTo | cut -c1-4`" = "true" ]; then
            curl -X POST -H 'Content-type:application/json' --data-binary '{
                "add-copy-field":{
                "source":"'"$name"'",
                "dest": "_text_" }
            }' http://$1:8983/solr/turing/schema
            
        fi
    done
} <$INPUT
IFS=$OLDIFS


###### Field Types

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "delete-field-type":{ "name":"text_spellcheck" }
}' http://$1:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
    "add-field-type": {
        "name": "text_spellcheck",
        "class": "solr.TextField",
        "positionIncrementGap": "100",
        "analyzer": {
            "charFilters": [
                {
                    "class": "solr.HTMLStripCharFilterFactory"
                }
            ],
            "tokenizer": {
                "class": "solr.StandardTokenizerFactory"
            },
            "filters": [
                {
                    "class": "solr.LowerCaseFilterFactory"
                },
                {
                    "class": "solr.SynonymFilterFactory",
                    "expand": "true",
                    "ignoreCase": "true",
                    "synonyms": "synonyms.txt"
                },
                {
                    "class": "solr.RemoveDuplicatesTokenFilterFactory"
                }
            ]
        }
    }
}' http://$1:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "delete-field-type":{ "name":"text_suggest" }
}' http://$1:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
    "add-field-type": {
        "name": "text_suggest",
        "class": "solr.TextField",
        "positionIncrementGap": "100",
        "indexAnalyzer": {
            "charFilters": [
                {
                    "class": "solr.HTMLStripCharFilterFactory"
                }
            ],
            "tokenizer": {
                "class": "solr.StandardTokenizerFactory"
            },
            "filters": [
                {
                    "class": "solr.LowerCaseFilterFactory"
                },
                {
                    "class": "solr.RemoveDuplicatesTokenFilterFactory"
                },
                {
                    "class": "solr.ShingleFilterFactory",
                    "outputUnigrams": "true",
                    "maxShingleSize": "10",
                    "tokenSeparator": " "
                }
            ]
        },
        "queryAnalyzer": {
            "charFilters": [
                {
                    "class": "solr.HTMLStripCharFilterFactory"
                }
            ],
            "tokenizer": {
                "class": "solr.StandardTokenizerFactory"
            },
            "filters": [
                {
                    "class": "solr.LowerCaseFilterFactory"
                },
                {
                    "class": "solr.RemoveDuplicatesTokenFilterFactory"
                }
            ]
        }
    }
}' http://$1:8983/solr/turing/schema

## Search Component

curl -X POST -H 'Content-type:application/json' -d '{
  "add-searchcomponent": {
    "name": "tur_suggest",
    "class": "solr.SpellCheckComponent",
    "spellchecker": {
        "name": "suggest",
        "classname": "org.apache.solr.spelling.suggest.Suggester",
        "lookupImpl":"org.apache.solr.spelling.suggest.fst.WFSTLookupFactory",
        "buildOnCommit": "true",
        "exactMatchFirst": true,
        "field": "text_suggest" },
    "queryAnalyzerFieldType": "phrase_suggest"
  }
}' http://$1:8983/solr/turing/config

curl -X POST -H 'Content-type:application/json' -d '{
  "add-searchcomponent": {
    "name": "tur_spellcheck",
    "class": "solr.SpellCheckComponent",
    "spellchecker": {
        "name": "default",
        "classname": "solr.DirectSolrSpellChecker",
        "field": "text_spellcheck",
        "distanceMeasure": "internal",
        "accuracy": 0.0,
         "maxEdits": 2,
         "minPrefix": 1,
         "maxInspections": 5,
         "minQueryLength": 4,
         "maxQueryFrequency": 0.00,
         "thresholdTokenFrequency": 0.0000},
    "queryAnalyzerFieldType": "textSpell"
  }
}' http://$1:8983/solr/turing/config

### Query Converter
curl -X POST -H 'Content-type:application/json' -d '{
  "add-queryconverter": {
    "name": "queryConverter",
    "class": "org.apache.solr.spelling.SuggestQueryConverter"
  }
}' http://$1:8983/solr/turing/config

### Request Handler
curl -X POST -H 'Content-type:application/json' -d '{
  "add-requesthandler": {
    "name": "/tur_suggest",
    "class": "org.apache.solr.handler.component.SearchHandler",
    "defaults": {
        "spellcheck": "true",
        "spellcheck.dictionary": "suggest",
        "spellcheck.onlyMorePopular": "true",
        "spellcheck.count": "50",
        "spellcheck.collate": "false"
       },
       "components": ["tur_suggest"]
  }
}' http://$1:8983/solr/turing/config

curl -X POST -H 'Content-type:application/json' -d '{
  "add-requesthandler": {
    "name": "/tur_spell",
    "class": "org.apache.solr.handler.component.SearchHandler",
    "startup":"lazy",
    "defaults": {
        "df": "text_spellcheck",
        "spellcheck.dictionary": "default",
        "spellcheck": "on",
        "spellcheck.extendedResults": "true",
        "spellcheck.count": "10",
        "spellcheck.alternativeTermCount": "5",
        "spellcheck.maxResultsForSuggest": "5",
        "spellcheck.collate": "true",
        "spellcheck.collateExtendedResults": "true",
        "spellcheck.maxCollationTries": "10",
        "spellcheck.maxCollations": "1"
       },
       "last-components": ["tur_spellcheck"]
  }
}' http://$1:8983/solr/turing/config
