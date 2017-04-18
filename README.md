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

* To use the Tomcat 8 with JDK 8, install this Docker with command:

```shell
$ docker pull ventura24/tomcat-8-jdk8
$ docker run -d -p 8080:8080 --name tomcat-8-jdk8 ventura24/tomcat-8-jdk8 
```
* To use CoreNLP, install and start this Docker with command:

```shell
$ docker pull motiz88/corenlp
$ docker run -d -p 9000:9000 --name corenlp motiz88/corenlp
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
* Eedit the rows of `vigServicesNLPEntities` table and change the `name` column with correct OpenNLP Models path.

## Tomcat
### Host and Port
Configure the Tomcat to use `localhost` host and `8080` port, because the Google OAuth2 has permission to this domain and port.

### MySQL Connection
* Copy the MySQL JDBC to `<TOMCAT_DIR>/lib`. [https://dev.mysql.com/downloads/connector/j/5.0.html](https://dev.mysql.com/downloads/connector/j/5.0.html)
* Configure `<TOMCAT_DIR>/conf/context.xml` to use `jdbc/VigletDS` Datasource, adding the following lines:

```xml
<Resource name="jdbc/VigletDS" auth="Container" 
type="javax.sql.DataSource" maxTotal="100" maxIdle="30" 
maxWaitMillis="10000" username="viglet" password="viglet" 
driverClassName="com.mysql.jdbc.Driver" 
url="jdbc:mysql://localhost:3306/viglet" />
```

### Static Resource Cache
The Resources element represents all the resources available to the web application. This includes classes, JAR files, HTML, JSPs and any other files that contribute to the web application. 

Configure `<TOMCAT_DIR>/conf/context.xml` to increase the size of static resource cache, adding the following line:

> **cacheMaxSize**	
> The maximum size of the static resource cache in kilobytes. If not specified, the default value is 10240 (10 megabytes). This value may be changed while the web application is running (e.g. via JMX). If the cache is using more memory than the new limit the cache will attempt to reduce in size over time to meet the new limit. 

```xml
<Resources cachingAllowed="true" cacheMaxSize="100000" />
```
More details: [http://tomcat.apache.org/tomcat-8.0-doc/config/resources.html](http://tomcat.apache.org/tomcat-8.0-doc/config/resources.html)

### OpenNLP Models
Copy the OpenNLP Models to Viglet Turing Web Application, access http://opennlp.sourceforge.net/models-1.5/ and copy the following models to `<VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/<LANGUAGE>/`. For example:

```shell
$ mkdir -p <VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/en
$ cd <VIGLET_TURING_REPOSITORY>/target/turing/WEB-INF/classes/models/opennlp/en
$ curl http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin -o en-ner-person.bin
```

### Bower
Bower is a command line utility. Install it with npm.

```shell
$ npm install -g bower
```

Bower requires node, npm and git.

More details: [https://bower.io/#install-bower](https://bower.io/#install-bower)

### Viglet Turing Deploy
* Access the Viglet Turing Git repository and run:

```shell
$ mvn package
```
* Copy the turing to Tomcat with command:

```shell
$ docker cp <VIGLET_TURING_REPOSITORY>/target/turing tomcat-8-jdk8:/usr/local/tomcat/webapps
```
## Authentication
### Obtain OAuth 2.0 credentials

You need OAuth 2.0 credentials, including a client ID and client secret, to authenticate users and gain access to Google's APIs.

To find your project's client ID and client secret, do the following:

1. Open the [Credentials page](https://console.developers.google.com/apis/credentials).
1. If you haven't done so already, create your project's OAuth 2.0 credentials by clicking **Create credentials > OAuth client ID**, and providing the information needed to create the credentials.
1. Look for the **Client ID** in the **OAuth 2.0 client IDs** section. For details, click the client ID.

More details: [https://developers.google.com/identity/protocols/OpenIDConnect](https://developers.google.com/identity/protocols/OpenIDConnect)

## Viglet Turing
* Administration Console [http://localhost:8080/turing](http://localhost:8080/turing).
* Semantic Navigation [http://localhost:8080/turing/sn](http://localhost:8080/turing/sn).

# For more information

* [Viglet website](https://viglet.ai)
* [Features](https://github.com/openviglet/turing/wiki/Features)
* [Compatibility Matrix](https://github.com/openviglet/turing/wiki/Compatibility-Matrix)
* [API Documentation](https://developers.viglet.ai)
* [Viglet Turing for WordPress](https://github.com/openviglet/turing4wp)