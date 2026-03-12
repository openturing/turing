set TURING_ADMIN_PASSWORD=turing
call mvn spring-boot:run -pl turing-app -Dskip.npm ^
"^
-Dspring-boot.run.arguments=--spring.h2.console.enabled=true ^
--logging.config=classpath:logback-spring-mongo.xml ^
--turing.mongodb.enabled=true ^
"