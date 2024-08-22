@ECHO OFF
java -Dloader.path=%~dp0libs -Dturing.url=http://localhost:2700 -Dturing.apiKey=968620e286c3483b829642b7f -jar turing-web-crawler.jar
