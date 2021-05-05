FROM adoptopenjdk/openjdk14:latest as turingbuild
WORKDIR /turing-src

VOLUME /root/.gradle

COPY . .
RUN ./gradlew build -x test -i --stacktrace

FROM adoptopenjdk/openjdk14:jre
WORKDIR /turing 
ENV JAVA_OPTS=${JAVA_OPTS:-'-Xmx512m'}
ENV DEBUG_OPTS=${DEBUG_OPTS}
ENV PORT=${PORT:-2700}
ENV spring.datasource.url=${DATA_SOURCE:-'jdbc:h2:file:./store/db/turingDB'}
ENV spring.datasource.username=${DB_USER:-'sa'}
ENV spring.datasource.password=${DB_PASSWORD:-''}
ENV spring.datasource.driver-class-name=${DB_DRIVER:-'org.h2.Driver'}
ENV spring.jpa.properties.hibernate.dialect=${DB_DIALECT:-'org.hibernate.dialect.H2Dialect'}

RUN useradd --system --create-home --uid 1001 --gid 0 java

RUN sh -c 'mkdir -p /turing/store'
RUN sh -c 'mkdir -p /turing/models'
RUN sh -c 'chown -R java /turing'
#COPY --from=turingbuild  /turing-src/build/libs/viglet-turing.jar /turing/viglet-turing.jar
COPY /build/libs/viglet-turing.jar /turing/viglet-turing.jar

RUN sh -c 'touch /turing/viglet-turing.jar'

VOLUME /turing/tmp
VOLUME /turing/store
VOLUME /turing/models

USER java

EXPOSE ${PORT}

CMD cd /turing && java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -jar ./viglet-turing.jar
