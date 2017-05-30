![viglet_logo.png](https://viglet.ai/img/banner/viglet_turing.png)
------
**Viglet Turing** is a platform that uses natural language processing (NLP) and machine learning to provide more intelligent data. Choose your favorite NLP as CoreNLP and OpenText Content Analytics, and index your content in Solr with higher added value. Train and manages models for your learning machine like Google Tensorflow.

**If you'd like to contribute to Viglet Turing, be sure to review the [contribution
guidelines](CONTRIBUTING.md).**

**We use [GitHub issues](https://github.com/openviglet/turing/issues) for
tracking requests and bugs.**

# Installation

## Maven
* Install the Apache Maven. [https://maven.apache.org/install.html](https://maven.apache.org/install.html)

## Docker
* Install the Docker. [https://docs.docker.com/engine/installation](https://docs.docker.com/engine/installation)
* To use the MySQL, install and start this Docker with command:

```shell
$ docker pull mysql
$ docker run -d --name mysql mysql
```

* To use Solr, install and start this Docker with command:

```shell
$ docker pull solr
$ docker run -d -p 8983:8983 --name solr solr
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

## MySQL

* Create the **viglet** user and **viglet** database into MySQL.
* Execute the script `<VIGLET_TURING_REPOSITORY>/sql/turing.sql` into viglet database.
* Edit the rows of `vigServices` table and change the `host` and `port` column to access your services.
* Edit the rows of `vigServicesNLPEntities` table and change the `name` column with correct OpenNLP Models path.

## OpenNLP Models

Copy the OpenNLP Models to Viglet Turing Web Application, access http://
opennlp.sourceforge.net/models-1.5/ and copy the following models to `<VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/<LANGUAGE>/`. For example:

```shell
$ mkdir -p <VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/en
$ cd <VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/en
$ curl http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin -o en-ner-person.bin
```

## Deploy 
### Bower
Bower is a command line utility. Install it with npm.

```shell
$ npm install -g bower
```

Bower requires node, npm and git.

More details: [https://bower.io/#install-bower](https://bower.io/#install-bower)

## Run

To run Viglet Turing with Jetty 9 embedded, just execute the following line:

```shell
$ mvn jetty:run
```

## Viglet Turing
* Administration Console [http://localhost:8080/turing](http://localhost:8080/turing).
* Semantic Navigation [http://localhost:8080/turing/sn](http://localhost:8080/turing/sn).

# For more information

* [Viglet website](https://viglet.ai)
* [Installation Guide](https://github.com/openviglet/turing/wiki/Installation)
* [Features](https://github.com/openviglet/turing/wiki/Features)
* [Compatibility Matrix](https://github.com/openviglet/turing/wiki/Compatibility-Matrix)
* [API Documentation](https://developers.viglet.ai)
* [Viglet Turing for WordPress](https://github.com/openviglet/turing4wp)
* [Viglet Turing Listener for WEM (OpenText Web Experience Management)](https://github.com/openviglet/turing-wem)
* [Viglet Turing SDK for PHP (Under development)](https://github.com/openviglet/turing-php-sdk)