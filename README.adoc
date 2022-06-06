= Viglet Turing AI: README
Viglet Team <opensource@viglet.com>
:organization: Viglet Turing
:toclevels: 5
:toc-title: Table of Content
:viglet-version: 0.3.4

[preface]
image:https://img.shields.io/badge/Download-Release%20{viglet-version}-blue?style=for-the-badge&logo=Java[link="https://viglet.com/turing/download/"]
image:https://img.shields.io/github/license/openturing/turing.svg?style=for-the-badge&logo=Apache["License"]
image:https://img.shields.io/github/last-commit/openturing/turing.svg?style=for-the-badge&logo=java)[GitHub last commit]
image:https://img.shields.io/github/workflow/status/openturing/turing/Build?style=for-the-badge&logo=GitHub[link="https://github.com/openturing/turing/actions/workflows/build.yml"]
image:https://img.shields.io/badge/Sonar-Code%20Quality-brightgreen?style=for-the-badge&logo=SonarCloud[link="https://sonarcloud.io/organizations/viglet-turing/projects"]

= Preface

Viglet Turing AI (https://openviglet.github.io/turing/) is an open source solution (https://github.com/openturing), which has Semantic Navigation and Chatbot as its main features. You can choose from several NLPs to enrich the data. All content is indexed in Solr as search engine.

:numbered:

== Development

=== Documentation

Technical documentation on Turing AI is available at https://openviglet.github.io/docs/turing/.

=== Run

To run Turing AI, just execute the following lines:

```shell
# Turing App
./gradlew turing-app:bootrun --args='--spring.profiles.active=dev-ui' -Pno-ui

# New Turing AI UI using Angular 12 and Primer CSS.

cd turing-ui

## Login
ng serve welcome

## Console
ng serve console

## Search
ng serve sn

## Chatbot
ng serve converse
```
=== Docker Compose

You can start the Turing AI using MariaDB, Solr and Nginx.

```shell
docker-compose up
```

=== URLs

* Administration Console: http://localhost:2700. (admin/admin)
* Semantic Navigation Sample: http://localhost:2700/sn/Sample.

== Architecture

[#img-architecture] 
.Turing AI Architecture  
image::img/turing-diagram.png[Architecture]  

== NLP

Turing support the followings providers:

=== OpenNLP
Apache OpenNLP is a machine learning based toolkit for the processing of natural language text.

Website: https://opennlp.apache.org/

=== OpenText Content Analytics
It transforms data into insights for better decision-making and information management while freeing up resources and time.

Website: https://www.opentext.com/

=== CoreNLP
CoreNLP is your one stop shop for natural language processing in Java! CoreNLP enables users to derive linguistic annotations for text, including token and sentence boundaries, parts of speech, named entities, numeric and time values, dependency and constituency parses, coreference, sentiment, quote attributions, and relations. CoreNLP currently supports 6 languages: Arabic, Chinese, English, French, German, and Spanish.

Website: https://stanfordnlp.github.io/CoreNLP/,

=== SpaCy
It is a free open-source library for Natural Language Processing in Python. It features NER, POS tagging, dependency parsing, word vectors and more.

Website: https://spacy.io

=== Polyglot NLP
Polyglot is a natural language pipeline that supports massive multilingual applications.

Website: https://polyglot.readthedocs.io

== Documents and OCR

It can read PDFs and Documents and convert to plain text and also it uses OCR to detect text in images and images into documents.

== Semantic Navigation

=== Connectors

Semantic Navigation uses Connectors to index the content from many sources.

==== Apache Nutch
Plugin for Apache Nutch to index content using crawler.

Learn more at https://docs.viglet.com/turing/connectors/#nutch

==== Database
Command line that uses the same concept as sqoop (https://sqoop.apache.org/), to create complex queries and map attributes to index based on the result.

Learn more at https://docs.viglet.com/turing/connectors/#database

==== File System
Command line to index files, extracting text from files such as Word, Excel, PDF, including images, through OCR.

Learn more at https://docs.viglet.com/turing/connectors/#file-system

==== OpenText WEM Listener
OpenText WEM Listener to publish content to Viglet Turing.

Learn more at https://docs.viglet.com/turing/connectors/#wem

==== Wordpress
Wordpress plugin that allows you to index posts.

Learn more at https://docs.viglet.com/turing/connectors/#wordpress


=== Named Entity Recognition (NER)
With NLP it is possible to detect entities such as:

* People
* Places
* Organizations
* Money
* Time
* Percentage

=== Facets
Define attributes that will be used as filters for your navigation, consolidating the total content in your display

=== Targeting Rules
Through attributes defined in the contents, it is possible to use them to restrict their display based on the user's profile.

=== SDK Java
Java API (https://github.com/openturing/turing-java-sdk) facilitates the use and access to Viglet Turing AI, without the need for consumer search content with complex queries.

== Chatbot
Communicate with your client and elaborate complex intents, obtain reports and progressively evolve your interaction.

Its components:

=== Agent
Handles conversations with your end users. It is a natural language processing module that understands the nuances of human language

=== Intent
An intent categorizes an end user's intention for taking a conversation shift. For each agent, you define several intents, where your combined intents can handle a complete conversation.

=== Actions
The field of action is a simple field of convenience that helps to execute logic in the service.

=== Entity
Each intent parameter has a type, called an entity type, that dictates exactly how the data in an end user expression is extracted.

=== Training
Defines and corrects intents.

=== History
Shows the conversation history and reports.

== OpenText Blazon Integration

Turing AI detects Entities of OpenText Blazon Documents using OCR and NLP, generating Blazon XML to show the entities into document.

[[turing-console]]
== Turing AI Console

Turing AI has many components: Search Engine, NLP, Converse (Chatbot), Semantic Navigation

[[turing-console-login]]
=== Login

When access the Turing AI, appear a login page. For default the login/password is `admin`/`admin`

[#img-login] 
.Login Page 
image::img/screenshots/turing-login.png[Login]  

<<<
[[turing-console-se]]
=== Search Engine

==== Configuration
Search Engine is used by Turing to store and retrieve data of Converse (Chatbot) and Semantic Navigation Sites.

[#img-se] 
.Search Engine Page
image::img/screenshots/turing-se.png[Search Page]

It is possible create or edit a Search Engine with following attributes:

.Search Engine Attributes
[%header,cols=2*] 
|===
| Attribute | Description
| Name | Name of Search Engine
| Description | Description of Search Engine
| Vendor | Select the Vendor of Search Engine. For now it only supports Solr.
| Host | Host name where the Search Engine service is installed
| Port | Port of Search Engine Service
| Language | Language of Search Engine Service.
| Enabled | If the Search Engine is enabled.
|===

<<<
[[turing-console-sn]]
=== Semantic Navigation

==== Configuration
[#img-sn] 
.Semantic Navigation Page
image::img/screenshots/turing-sn.png[Semantic Navigation Page]

[[turing-console-sn-detail-tab]]
===== Detail Tab

The Detail of Semantic Navigation Site contains the following attributes:

.Semantic Navitation Site Detail
[%header,cols=2*] 
|===
| Attribute | Description
| Name | Name of Semantic Navigation Site.
| Description | Description of Semantic Navigation Site.
| Search Engine | Select the Search Engine that was created in Search Engine Section. The Semantic Navigation Site will use this Search Engine to store and retrieve data.
| NLP | Select the NLP that was created in NLP Section. THe Semantic Navigation Site will use this NLP to detect entities during indexing.
| Thesaurus | If will use Thesaurus.
| Language | Language of Semantic Navigation Site.
| Core | Name of core of Search Engine where will be stored and retrieved the data.
|===

<<<
[[turing-console-sn-fields-tab]]
===== Fields Tab

Fields Tab contains a table with the following columns:
.Semantic Navitation Site Fields Columns
[%header,cols=2*] 
|===
| Column Name | Description
| Type | Type of Field. It can be: 

- NER (Named Entity Recognition) used by NLP.

- Seach Engine used by Solr.
| Field | Name of Field.
| Enabled | If the field is enabled or not.
| MLT | If this field will be used in MLT.
| Facets | To use this field like a facet (filter)
| Highlighting | If this field will show highlighted lines.
| NLP | If this field will be processed by NLP to detect Entities (NER) like People, Organization and Place.
|===

When click in Field appear a new page with Field Details with the following attributes:

.Semantic Navitation Site Fields Detail Attributes
[%header,cols=2*] 
|===
| Attribute | Description
| Name | Name of Field
| Description | Description of Field
| Type | Type of Field. It can be: `INT`, `LONG`, `STRING`, `DATE` and `BOOL`
| Multi Valued | If is a array
| Facet Name | Name of Label of Facet (Filter) on Search Page.
| Facet | To use this field like a facet (filter)
| Highlighting | If this field will show highlighted lines.
| MLT | If this field will be used in MLT.
| Enabled |  If the field is enabled.
| Required | If the field is required.
| Default Value | Case the content is indexed without these field, that is the default value.
| NLP |  If this field will be processed by NLP to detect Entities (NER) like People, Organization and Place.
|===

<<<
[[turing-console-sn-appearance-tab]]
===== Appearance Tab

Contains the following attributes:

.Semantic Navitation Site Appearance Attributes
[%header,cols=3*] 
|===
| Section | Attribute | Description
| Appearance| Number of items per page | Number of items that will appear in search.
.2+| Facet | Facet enabled? | If it will be show Facet (Filters) on search.
| Number of items per facet | Number of items that will appear in each Facet (Filter).
.3+| Highlighting | Highlighting enabled? | Define whether to show highlighted lines.
| Pre Tag | HTML Tag that will be used on begin of term. For example: <mark>
| Post Tag | HTML Tag that will be used on the end of term. For example: </mark>
| MLT | More Like This enabled? | Define whether to show MLT
.6+| Default Fields | Title | Field that will be used as title that is defined in Solr schema.xml
| Text | Field that will be used as title that is defined in Solr schema.xml
| Description | Field that will be used as description that is defined in Solr schema.xml
| Date | Field that will be used as date that is defined in Solr schema.xml
| Image | Field that will be used as Image URL that is defined in Solr schema.xml
| URL | Field that will be used as URL that is defined in Solr schema.xml
|===

<<<
[[turing-console-sn-site-page]]
==== Site Page

[[turing-sn-site-page-html]]
===== HTML
In `Turing AI Console` > `Semantic Navigation` > `<SITE_NAME>`, click in `Configure` button and click `Search Page` button. 

It will open a Search Page that uses the pattern:

....
GET http://localhost:2700/sn/<SITE_NAME>
....

[[turing-console-sn-site-page-json]]
===== JSON
This page requests the Turing Rest API via AJAX. For example, to return all results of Semantic Navigation Site in JSON Format: 

....
GET http://localhost:2700/api/sn/<SITE_NAME>/search?p=1&q=*&sort=relevance
....

.Semantic Navigation Rest API Get Attributes
[%header,cols=4*] 
|===
| Attribute | Required / Optional | Description | Example
| q | Required | Search Query. | q=foo
| p | Required | Page Number, first page is 1. | p=1
| sort | Required | Sort values: `relevance`, `newest` and `oldest`. | sort=relevance
| fq[] | Optional | Query Field. Filter by field, using the following pattern: *FIELD*: *VALUE*. | fq[]=title:bar
| tr[] | Optional | Targeting Rule. Restrict search based in: *FIELD*: *VALUE*. | tr[]=department:foobar
| rows | Optional | Number of rows that query will return. | rows=10
|===
== Customer Case Studies

=== Insurance Company
On Intranet of Insurance Company uses OpenText WEM and OpenText Portal integrated with Dynamic Portal Module, a consolidated search was created in Viglet Turing AI, using the connectors: WEM, Database with File System. In this way it was possible to display all the contents and files of the search Intranet, with targeting rules, allowing only to display content that the user has permission. The OpenText Portal accesses Viglet Turing AI Java API, so it was not necessary to create complex queries to return the results.

=== Government Company
A set of API Rest was created to make all Government Company content available to partners. All these contents are in OpenText WEM and the WEM connector was used to index the contents on Viglet Turing AI. A Spring Boot application was created with the Rest API set that consumes Turing AI content through the Viglet Turing AI Java API.

=== Brazilian University
Brazilian University website was developed using Viglet Shio CMS (https://viglet.com/shio), and all contents are indexed in Viglet Turing AI automatically. This configuration was made in content modeling and the development of the search template was made in Viglet Shio CMS.
