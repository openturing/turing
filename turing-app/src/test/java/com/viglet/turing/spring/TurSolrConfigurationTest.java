package com.viglet.turing.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.junit.jupiter.api.Test;

class TurSolrConfigurationTest {

    @Test
    void shouldCreateConcurrentMapForSolrClientCache() {
        TurSolrConfiguration configuration = new TurSolrConfiguration();

        Map<String, HttpJdkSolrClient> cache = configuration.solrClientCache();

        assertThat(cache).isInstanceOf(ConcurrentHashMap.class).isEmpty();
    }

    @Test
    void shouldReturnNewMapInstanceOnEachCall() {
        TurSolrConfiguration configuration = new TurSolrConfiguration();

        Map<String, HttpJdkSolrClient> first = configuration.solrClientCache();
        Map<String, HttpJdkSolrClient> second = configuration.solrClientCache();

        assertThat(first).isNotSameAs(second);
    }
}
