[![downloads](https://img.shields.io/github/downloads/openturing/turing/total.svg)](https://github.com/openturing/turing/releases/download/v0.3.3/viglet-turing.jar)
[![Build](https://github.com/openturing/turing/actions/workflows/build.yml/badge.svg)](https://github.com/openturing/turing/actions/workflows/build.yml) [![codecov](https://codecov.io/gh/openturing/turing/branch/master/graph/badge.svg)](https://codecov.io/gh/openturing/turing) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=openturing_turing&metric=alert_status)](https://sonarcloud.io/dashboard/index/openturing_turing)
[![Twitter](https://img.shields.io/twitter/follow/VigletTweet.svg?style=social&label=Follow)](https://twitter.com/intent/follow?screen_name=VigletTweet)

------

**Viglet Turing** - Semantic Navigation, Chatbot using Search Engine and Many NLP Vendors.

**If you'd like to contribute to Viglet Turing, be sure to review the [contribution
guidelines](CONTRIBUTING.md).**

**We use [GitHub issues](https://github.com/openshio/turing/issues) for
tracking requests and bugs.**

# Installation

## Pre-reqs
1. Install Java 14
2. Install Git Client

## Docker
* Install Docker. [https://docs.docker.com/engine/installation](https://docs.docker.com/engine/installation)

* To use Solr, install and start this Docker with command:

```shell
$ docker pull solr
$ docker run -d -p 8983:8983 --name solr solr
```

```bash
$ cd TURING_DIR
$ ./gradlew build && docker build -t viglet-turing .
$ docker run -d -p 2700:2700 --name viglet-turing viglet-turing 
```

## Solr
### Create Core

Create the **turing** core into Solr with command:

```shell
$ docker exec -it solr bash
$ cd /opt/solr/bin
$ ./solr create -c turing
```

### Create Fields

Create the default fields into Solr with command:

```shell
$ curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"turing_entity_PN",
     "type":"text_general",
     "indexed":true,
     "stored":true }
}' http://localhost:8983/solr/turing/schema

$ curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"turing_entity_ON",
     "type":"text_general",
     "indexed":true,
     "stored":true }
}' http://localhost:8983/solr/turing/schema

$ curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"turing_entity_GL",
     "type":"text_general",
     "indexed":true,
     "stored":true }
}' http://localhost:8983/solr/turing/schema
$ curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"turing_entity_IPTC",
     "type":"text_general",
     "indexed":true,
     "stored":true }
}' http://localhost:8983/solr/turing/schema
$ curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"turing_entity_ComplexConcepts",
     "type":"text_general",
     "indexed":true,
     "stored":true }
}' http://localhost:8983/solr/turing/schema
```
### Highlighting

**Solr Highlighting** to work requires that the fields that use this feature be **text_general** type. So add or modify the following fields in `/opt/solr/server/solr/turing/conf/managed-schema` file.

```xml
  <field name="text" type="text_general" indexed="true" stored="true"/>
  <field name="title" type="text_general" indexed="true" stored="true"/>

```
### Chat

Create the converse core int Solr.
```bash
# solr create -c converse
```

So add or modify the following fields in `/opt/solr/server/solr/converse/conf/managed-schema` file.

```xml
  <field name="id" type="string" multiValued="false" indexed="true" required="true" stored="true"/>
  <field name="description" type="text_general" multiValued="false" indexed="true" stored="true"/>
  <field name="action" type="text_general"/>
  <field name="agent" type="text_general" indexed="true" stored="true"/>
  <field name="contextInput" type="text_general" multiValued="true" indexed="true" stored="true"/>
  <field name="contextOutput" type="text_general" multiValued="true" indexed="true" stored="true"/>
  <field name="hasParameters" type="boolean"/>
  <field name="intent" type="text_general"/>
  <field name="name" type="text_general"/>
  <field name="position" type="plong"/>
  <field name="phrases" type="text_general" multiValued="true" indexed="true" stored="true"/>
  <field name="prompts" type="text_general" multiValued="true" indexed="true" stored="true"/>
  <field name="responses" type="text_general" multiValued="true" indexed="true" stored="true"/>
```
## Deploy 
### Generate Fat Jar File

Use Gradle to generate a Viglet Turing Fat Jar file.

```shell
$ ./gradlew build
```

## Run

To run Viglet Turing Fat Jar file, just execute the following line:

```shell
$ java -jar build/libs/turing-0.1.0.jar
```

## Viglet Turing
* Administration Console [http://localhost:2700](http://localhost:2700).
* Semantic Navigation [http://localhost:2700/sn](http://localhost:2700/sn).

# For more information

* [Viglet website](https://viglet.com)
* [Installation Guide](https://github.com/openturing/turing/wiki/Installation-Guide)
* [Features](https://github.com/openturing/turing/wiki/Features)
* [Compatibility Matrix](https://github.com/openturing/turing/wiki/Compatibility-Matrix)
* [API Documentation](https://developers.viglet.com)
* [Viglet Turing for WordPress](https://github.com/openturing/turing4wp)
* [Viglet Turing Listener for WEM (OpenText Web Experience Management)](https://github.com/openturing/turing-wem)
* [Viglet Turing SDK for PHP (Under development)](https://github.com/openturing/turing-php-sdk)
