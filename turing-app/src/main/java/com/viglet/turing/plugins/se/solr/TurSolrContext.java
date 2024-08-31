package com.viglet.turing.plugins.se.solr;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.plugins.se.TurSeContext;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.solr.TurSolrCache;
import com.viglet.turing.solr.TurSolrInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Optional;

@Slf4j
@Component
public class TurSolrContext implements TurSeContext {
    private final TurSolrCache turSolrCache;
    private final CloseableHttpClient closeableHttpClient;
    private final TurConfigProperties turConfigProperties;
    @Inject
    public TurSolrContext(TurSolrCache turSolrCache, CloseableHttpClient closeableHttpClient,
                          TurConfigProperties turConfigProperties) {
        this.turSolrCache = turSolrCache;
        this.closeableHttpClient = closeableHttpClient;
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    public Object getConnection(TurSEInstance turSEInstance) {
        return getSolrClient(turSEInstance, "default");
    }

    private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
        String urlString = String.format("http://%s:%s/solr/%s", turSEInstance.getHost(), turSEInstance.getPort(),
                core);
        if (turSolrCache.isSolrCoreExists(urlString)) {
            HttpSolrClient httpSolrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
                    .withConnectionTimeout(turConfigProperties.getSolr().getTimeout())
                    .withSocketTimeout(turConfigProperties.getSolr().getTimeout()).build();
            try {
                return Optional.of(new TurSolrInstance(closeableHttpClient, httpSolrClient, URI.create(urlString).toURL(), core));
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

}
