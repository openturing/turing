[![Build](https://github.com/openturing/turing-wem/actions/workflows/build.yml/badge.svg)](https://github.com/openturing/turing-wem/actions/workflows/build.yml)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=openturing_turing-wem&metric=security_rating)](https://sonarcloud.io/dashboard?id=openturing_turing-wem)
[![viglet_logo.png](https://openturing.github.io/turing/img/banner/viglet_turing.png)](http://viglet.com/turing)

# Listener WEM for Viglet Turing 
Open Text Web Experience Management (WEM) Listener to publish content to Viglet Turing


## Deploy 
### 1. WEM Jar files
Copy the following jar files to /lib directory:

* \<WEM\_DIR>/Content/\<VERSION>/lib/sdk/thirdparty-combined.jar
* \<WEM\_DIR>/Content/\<VERSION>/lib/sdk/vgn-appsvcs-cma.jar
* \<WEM\_DIR>/Content/\<VERSION>/lib/vgn-shared-logging.jar

### 2. Compile

Use Gradle to compile the listener jar file, without generate jar file.

Install the JDK 8 to Gradle

Create the variable JDK6_HOME with JDK6 Path.

```shell
export JDK6_HOME=/opt/WEM/Content/VERSION/java
$ ./gradlew shadowJar
```

### 3. Configuration

#### WEM Deploy

Add the turing-wem-all.jar into WEM using configp

```shell

$ ./configp
============================================================

Configuration Program Main Menu

-----------------------------------------
   1.  Connect to WEM Server
   2.  Create a Disconnected Configuration Agent
   3.  Remove a Disconnected Configuration Agent
   4.  Repair Management Server

   q.  Quit

   > 1
============================================================
Connect to WEM Server: WEM Server Connection Information


WEM Server host: wemserver
WEM Server port: 27110
WEM Server administrator: vgnadmin
WEM Server administrative password:

*****************************************
 You have entered the following:

  WEM Server host = wemserver
  WEM Server port = 27110
  WEM Server administrator = vgnadmin
  WEM Server administrative password = ********


Is this correct ( (y)es, (n)o, (b)ack, (c)ancel )?[y]:
Connecting...
Connected to t3://wemserver:27110
============================================================

Managing Configuration Services
-----------------------------------------
   1.  Manage a Product Instance
   2.  Create a Configuration Agent
   3.  Remove a Configuration Agent
   4.  Register a Configuration Agent
   5.  Manage Applications
   6.  List Configuration Settings

   b.  Back
   q.  Quit

   > 5
============================================================
Manage Applications: Manage Application


  To register or unregister Extension Modules, select
  Register Product Extensions. To modify an existing
  deployed application, select Update Runtime Services.

Select type of application update
---------------------------------
   1.  Register Product Extensions
   2.  Update Runtime Services

   b.  Back
   c.  Cancel

   > 1

*****************************************
 You have entered the following:

  Select type of application update = Register Product Extensions


Is this correct ( (y)es, (n)o, (b)ack, (c)ancel, (u)ndo )?[y]:
============================================================
Manage Applications: Deployment Types


  You can choose to deploy an extension which exists
  within the VCM ear container or a standalone application
  outside of the VCM ear container.

Do you want to deploy an extension or standalone application?
--------------------------------------------------
   1.  Extension
   2.  Standalone Application

   b.  Back
   c.  Cancel

   > 1

*****************************************
 You have entered the following:

  Do you want to deploy an extension or standalone application? = Extension


Is this correct ( (y)es, (n)o, (b)ack, (c)ancel, (u)ndo )?[y]:
============================================================
Manage Applications: Deployment Actions


Register Extension Type
-----------------------
   1.  JAR Extension Module
   2.  WAR Extension Module
   3.  Multiple Extension Modules - can include both JAR and WAR files

   b.  Back
   c.  Cancel

   > 1
Deployment Action
-----------------
   1.  Deploy Extension
   2.  Undeploy Extension

   b.  Back
   c.  Cancel

   > 1

*****************************************
 You have entered the following:

  Register Extension Type = jarext (JAR Extension Module)
  Deployment Action = Deploy Extension


Is this correct ( (y)es, (n)o, (b)ack, (c)ancel, (u)ndo )?[y]:
============================================================
Manage Applications: Extension JAR Path


  Enter the path to the archive file containing the
  extension. This file is registered with the repository
  and deployed to the application server.

  Important!! Deployment of an extension could take
  up to 15 mins.

JAR Path (example: C:\vign_extn.jar): /opt/viglet/turing-wem/listener/turing-wem-all.jar

*****************************************
 You have entered the following:

  JAR Path (example: C:\vign_extn.jar) = /opt/viglet/turing-wem/listener/turing-wem-all.jar


Is this correct ( (y)es, (n)o, (b)ack, (c)ancel, (u)ndo )?[y]: y
============================================================
Manage Applications: Confirm Configuration


  Are you ready to perform this action?



Continue? ( (y)es, (n)o, (b)ack, (c)ancel )? [y]: y

Confirm Configuration:

  All the information has been collected. Would you
  like to commit the configuration? (y/n) [y]: y

Step 1 of 3: Validating Input ...
Step 2 of 3: Check Configuration Status ...
Step 3 of 3: Updating Application ...

Success:

The configuration wizard completed successfully.

```

#### Mapping

Create a /opt/viglet/turing-wem/conf/CTD-Turing-Mappings.xml file with the following lines:

```xml
<?xml version="1.0" encoding="UTF-8" ?>

<mappingDefinitions>
	<common-index-attrs>

		<srcAttr xmlName="TYPE" mandatory="true">
			<tag>type</tag>
		</srcAttr>

		<srcAttr xmlName="CONCEPTS-KEYWORDS" mandatory="true">
			<tag>turingSimpleConcept</tag>
		</srcAttr>
		<srcAttr xmlName="PEOPLE" mandatory="true">
			<tag>turingPN</tag>
		</srcAttr>
		<srcAttr xmlName="PLACES" mandatory="true">
			<tag>turingGL</tag>
		</srcAttr>
		<srcAttr xmlName="ORGANIZATIONS" mandatory="true">
			<tag>turingON</tag>
		</srcAttr>
		<srcAttr xmlName="TONE" mandatory="true">
			<tag>turingSentimentTone</tag>
		</srcAttr>
		<srcAttr xmlName="SENTIMENT" mandatory="true">
			<tag>turingSentimentSubj</tag>
		</srcAttr>

		<srcAttr className="com.viglet.turing.ext.DPSUrl" mandatory="true">
			<tag>url</tag>
		</srcAttr>
	</common-index-attrs>

</mappingDefinitions>
```

#### Resource

Create a Resource called VigletTuring, type Properties and add the following lines:

```properties
cda.default.urlprefix=http://localhost
cda.default.contextname=sites
cda.default.hasContext=true
cda.default.hasSiteName=true
cda.default.hasFormat=true

turing.url=http://localhost:2700/api/otsn/broker
turing.locale=en
turing.index=Sample
turing.config=wem
turing.mappingsxml=/opt/viglet/turing-wem/conf/CTD-Turing-Mappings.xml
```

#### Events

Deployment.ManagedObjectCreate

```shell
com.viglet.turing.wem.listener.DeploymentEventListener
```

Deployment.ManagedObjectUpdate

```shell
com.viglet.turing.wem.listener.DeploymentEventListener
```

Deployment.ManagedObjectDelete

```shell
com.viglet.turing.wem.listener.DeploymentEventListener
```
