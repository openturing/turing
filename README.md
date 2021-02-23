[![Build Status](https://travis-ci.com/openturing/turing.svg?branch=master)](https://travis-ci.org/openturing/turing) [![codecov](https://codecov.io/gh/openturing/turing/branch/master/graph/badge.svg)](https://codecov.io/gh/openturing/turing) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=openturing_turing&metric=alert_status)](https://sonarcloud.io/dashboard/index/openturing_turing)

[![viglet_logo.png](https://openturing.github.io/turing/img/banner/viglet_turing.png)](http://viglet.com/turing)
------

**Viglet Turing** is a platform that uses natural language processing (NLP) and machine learning to provide more intelligent data. Choose your favorite NLP as CoreNLP and OpenText Content Analytics, and index your content in Solr with higher added value.

**If you'd like to contribute to Viglet Turing, be sure to review the [contribution
guidelines](CONTRIBUTING.md).**

**We use [GitHub issues](https://github.com/openshio/turing/issues) for
tracking requests and bugs.**

# Installation
## Generate new keystore
```shell
keytool -genkeypair -alias turing -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore turing.p12 -validity 3650
```

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

* [Viglet website](https://viglet.ai)
* [Installation Guide](https://github.com/openviglet/turing/wiki/Installation-Guide)
* [Features](https://github.com/openviglet/turing/wiki/Features)
* [Compatibility Matrix](https://github.com/openviglet/turing/wiki/Compatibility-Matrix)
* [API Documentation](https://developers.viglet.ai)
* [Viglet Turing for WordPress](https://github.com/openviglet/turing4wp)
* [Viglet Turing Listener for WEM (OpenText Web Experience Management)](https://github.com/openviglet/turing-wem)
* [Viglet Turing SDK for PHP (Under development)](https://github.com/openviglet/turing-php-sdk)
