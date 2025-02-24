set TAG_NAME=v2025.1
mvn build-helper:parse-version versions:set -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.incrementalVersion}.${parsedVersion.nextBuildNumber} versions:commit
mvn clean package
rem gh release create v2025.1 --generate-notes
gh release upload %TAG_NAME% turing-aem\aem-cli-indexer\target\turing-aem-cli.jar --clobber
gh release upload %TAG_NAME% turing-aem\aem-plugin\target\aem-plugin.jar --clobber
gh release upload %TAG_NAME% turing-app\target\viglet-turing.jar --clobber
gh release upload %TAG_NAME% turing-connector\connector-app\target\turing-connector.jar --clobber
gh release upload %TAG_NAME% turing-db\db-app\target\turing-db.jar --clobber
gh release upload %TAG_NAME% turing-filesystem\fs-connector\target\turing-filesystem.jar --clobber
gh release upload %TAG_NAME% turing-sprinklr\sprinklr-plugin\target\sprinklr-plugin.jar --clobber
gh release upload %TAG_NAME% turing-web-crawler\wc-plugin\target\web-crawler-plugin.jar --clobber
gh release upload %TAG_NAME% turing-java-sdk\target\turing-java-sdk.jar --clobber
gh release upload %TAG_NAME% turing-nutch\nutch1_20\target\turing-nutch120.jar --clobber
gh release upload %TAG_NAME% turing-commons\target\turing-commons.jar --clobber
gh release upload %TAG_NAME% turing-utils\target\turing-utils.zip --clobber