FROM adoptopenjdk/openjdk14:jre
ENV JAVA_OPTS=${JAVA_OPTS:-'-Xmx512m'}
ENV DEBUG_OPTS=${DEBUG_OPTS}
ENV PORT=${PORT:-2700}
ENV spring.datasource.url=${DATA_SOURCE:-'jdbc:h2:file:./store/db/turingDB'}
ENV spring.datasource.username=${DB_USER:-'sa'}
ENV spring.datasource.password=${DB_PASSWORD:-''}
ENV spring.datasource.driver-class-name=${DB_DRIVER:-'org.h2.Driver'}
ENV spring.jpa.properties.hibernate.dialect=${DB_DIALECT:-'org.hibernate.dialect.H2Dialect'}

RUN useradd --system --create-home --uid 1001 --gid 0 java

RUN sh -c 'mkdir -p /tmp'
RUN sh -c 'mkdir -p /store'
RUN sh -c 'mkdir -p /models'
RUN sh -c 'chown -R java /tmp'
RUN sh -c 'chown -R java /store'
RUN sh -c 'chown -R java /models'

COPY /turing-app/build/libs/viglet-turing.jar /viglet-turing.jar

RUN sh -c 'touch /viglet-turing.jar'

VOLUME /tmp
VOLUME /store
VOLUME /models

USER java

EXPOSE ${PORT}

CMD java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dlogging.config=classpath:logback-spring-console.xml -jar /viglet-turing.jar
