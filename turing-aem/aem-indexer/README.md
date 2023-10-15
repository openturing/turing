## Build

```bash
gradlew turing-aem:aem-indexer:shadowJar
```

## Create a turing-aem-all.properties
Create a turing-aem.properties file in same directory of turing-aem.jar for example
```properties
turing.url=http://localhost:2700
turing.login=admin
turing.password=admin
turing.mappingsxml=../../conf/CTD-Turing-Mappings.xml
turing.provider.name=AEM

dps.site.default.sn.site=Sample
dps.site.default.sn.locale=en_US
dps.site.default.contextname=sites
dps.site.default.urlprefix=http://example.com
dps.config.association.priority=Sample
dps.config.filesource.path=/appl/aem/
```
## Run Command Line

java -jar turing-aem.jar -h http://localhost:4502/crx/server -u admin -p admin -c cq:Page -s /content/we-retail