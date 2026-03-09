package com.viglet.turing.spring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TurSolrConfiguration {
    @Bean
    public Map<String, HttpJdkSolrClient> solrClientCache() {
        return new ConcurrentHashMap<>();
    }
}