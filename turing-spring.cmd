:: call mvn clean install -pl turing-commons
:: call mvn clean install -pl turing-java-sdk
call mvn -Dmaven.repo.local=D:\repo spring-boot:run -pl turing-app -Dskip.npm