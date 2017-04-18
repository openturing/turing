curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"turing_entity_ON",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"turing_entity_GL",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"turing_entity_IPTC",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema
curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"turing_entity_ComplexConcepts",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_segmento",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_momento",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_tipo_solucao",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_tema",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_uf",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
"add-field":{
"name":"sebna_tipo",
"type":"text_general",
"indexed":true,
"stored":true }
}' http://localhost:8983/solr/turing/schema
