[![viglet_logo.png](https://openturing.github.io/turing/img/banner/viglet_turing.png)](http://viglet.com/turing)

# turing-filesystem
File System Connector to import content to Viglet Turing
# Installation

## Download

```shell
$ git clone https://github.com/openturing/turing-filesystem.git
$ cd turing-filesystem
```

## Deploy

Use Gradle to generate Turing FileSystem Connector executable JAR file.

```shell
$ ./gradlew shadowJar
```

#### 2.1 Run

To run Turing FileSystem Connector executable JAR file, just execute the following line:

```shell
$ java -jar build/libs/turing-filesystem.jar <PARAMETERS>
```
## Example
```shell
$ java -jar build/libs/turing-filesystem.jar --server http://localhost:2700 --nlp b2b4a1ff-3ea3-4cec-aa95-f54d0f5f3ff8 --source-dir /appl/myfiles --output-dir /appl/results
```
