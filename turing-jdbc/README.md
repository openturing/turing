[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=openturing_turing-jdbc&metric=security_rating)](https://sonarcloud.io/dashboard?id=openturing_turing-jdbc)
[![viglet_logo.png](https://openturing.github.io/turing/img/banner/viglet_turing.png)](http://viglet.com/turing)

# Turing JDBC Connector
JDBC Connector to import content to Viglet Turing AI Semantic Navigation.

# Installation

## Download

```shell
$ git clone https://github.com/openturing/turing-jdbc.git
$ cd turing-jdbc
```

## Deploy 

Use Gradle to generate Turing JDBC Connector executable JAR file.

```shell
$ ./gradlew shadowJar
```

#### 2.1 Run

To run Turing JDBC Connector executable JAR file, just execute the following line:

```shell
$ java -jar build/libs/turing-jdbc.jar <PARAMETERS>
```

# Parameters

## Required
*--connect, -c*

	Specify JDBC connect string

*--driver, -d*

	Manually specify JDBC driver class to use

*--query, -q*

	Import the results of statement

*--site*

	Specify the Semantic Navigation Site
                        
## Optional

 *--chunk, -z*
 
	Number of items to be sent to the queue
	Default: 100
 
 *--class-name*
      
	Customized Class to modified rows
            
*--deindex-before-importing*

	Deindex before importing
	Default: false
      
*--encoding*

	Encoding Source
	Default: UTF-8
      
*--file-content-field*

	Field that shows Content of File
      
*--file-path-field*

	Field with File Path
      
*--file-size-field*

	Field that shows Size of File in bytes
	
*--help*

	Print usage instructions

*--include-type-in-id, -i*

	Include Content Type name in Id
	Default: false

*--max-content-size*

	Maximum size that content can be indexed (megabytes)
	Default: 5
      
*--multi-valued-field*

	Multi Valued Fields
      
*--multi-valued-separator*

	Multi Valued Separator
	Default: ,

*--password, -p*

	Set authentication password

*--remove-html-tags-field*

	Remove HTML Tags into content of field
      
*--server, -s*

	Viglet Turing Server
	Default: http://localhost:2700
      
*--show-output, -o*

	Show Output
	Default: false
            
*--type, -t*

	Set Content Type name
	Default: CONTENT_TYPE
      
*--username, -u*

	Set authentication username

# Example

```shell
java -jar ./turing-jdbc.jar --deindex-before-importing true \
--include-type-in-id true -z 1 \
--file-path-field filePath --file-content-field text \
--file-size-field fileSize -t Document \
--multi-valued-separator ";" --multi-valued-field field1,field2 \
--class-name com.viglet.turing.tool.ext.TurJDBCCustomSample \
-d com.mysql.jdbc.Driver -c jdbc:mysql://localhost/sampleDB  \
-q "select * from sampleTable" -u sampleUser -p samplePassword
```
