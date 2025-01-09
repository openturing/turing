# Parse the version using Maven and set the new version
$mvnCommand = 'mvn build-helper:parse-version versions:set -DnewVersion=`"$($parsedVersion.majorVersion).$($parsedVersion.minorVersion).$($parsedVersion.nextIncrementalVersion)`" versions:commit'
Invoke-Expression $mvnCommand

# Get the tag name based on the current project version
$tagName = Invoke-Expression "mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version -q -DforceStdout"
$tagName = "v$tagName"
Write-Host $tagName

# Stage changes, commit, and push to the repository
git add .
git commit -m "$tagName Release"
git push -u origin

# Install the package using Maven
Invoke-Expression "mvn install package"

# Create a GitHub release and upload the jar file
Invoke-Expression "gh release create $tagName --generate-notes"
Invoke-Expression "gh release upload $tagName turing-aem/aem-cli-indexer/target/turing-aem-cli.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-aem/aem-plugin/target/aem-plugin.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-app/target/viglet-turing.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-connector/connector-app/target/turing-connector.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-db/db-app/target/turing-db.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-filesystem/fs-connector/target/turing-filesystem.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-sprinklr/sprinklr-plugin/target/sprinklr-plugin.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-web-crawler/wc-plugin/target/web-crawler-plugin.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-java-sdk/target/turing-java-sdk.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-nutch/nutch1_20/target/turing-nutch120.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-commons/target/turing-commons.jar --clobber"
Invoke-Expression "gh release upload $TAG_NAME turing-utils/target/turing-utils.zip --clobber"