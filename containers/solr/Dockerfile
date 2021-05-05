FROM solr:7.6
VOLUME /opt/solr/server/solr

RUN mkdir -p /opt/solr/turing/config 
RUN mkdir -p /opt/solr/turing/script
COPY --chown=solr /config/solr/ /opt/solr/turing/config/
COPY --chown=solr /config/script/ /opt/solr/turing/script/
RUN sed -e "s/\r//g" /opt/solr/turing/script/copyConfig.sh > /opt/solr/turing/script/copyConfigUnix.sh
RUN chmod 777 /opt/solr/turing/script/copyConfigUnix.sh
RUN sh -c /opt/solr/turing/script/copyConfigUnix.sh
