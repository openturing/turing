/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.client.RestClient;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches {@link TurElasticsearchInstance} objects per endpoint+index.
 * Mirrors {@code TurLuceneInstanceProcess} for the Elasticsearch engine.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class TurElasticsearchInstanceProcess {

    private final ConcurrentHashMap<String, TurElasticsearchInstance> instanceCache =
            new ConcurrentHashMap<>();
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;

    public TurElasticsearchInstanceProcess(
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteRepository turSNSiteRepository) {
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
    }

    // -------------------------------------------------------------------------
    // Public init methods
    // -------------------------------------------------------------------------

    public Optional<TurElasticsearchInstance> initElasticsearchInstance(String siteName, Locale locale) {
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .flatMap(site -> initElasticsearchInstance(site, locale));
    }

    public Optional<TurElasticsearchInstance> initElasticsearchInstance(TurSEInstance turSEInstance,
            String index) {
        return getElasticsearchInstance(turSEInstance, index);
    }

    public Optional<TurElasticsearchInstance> initElasticsearchInstance(TurSNSiteLocale turSNSiteLocale) {
        return getElasticsearchInstance(
                turSNSiteLocale.getTurSNSite().getTurSEInstance(),
                turSNSiteLocale.getCore());
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private Optional<TurElasticsearchInstance> initElasticsearchInstance(TurSNSite turSNSite,
            Locale locale) {
        log.debug("initElasticsearchInstance site={} locale={}", turSNSite.getName(), locale);
        TurSNSiteLocale turSNSiteLocale =
                turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
        if (turSNSiteLocale == null) {
            log.warn("{} site with {} locale not found", turSNSite.getName(), locale);
            return Optional.empty();
        }
        log.debug("Found locale core={}", turSNSiteLocale.getCore());
        return getElasticsearchInstance(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
    }

    private Optional<TurElasticsearchInstance> getElasticsearchInstance(TurSEInstance turSEInstance,
            String index) {
        String normalizedIndex = index.toLowerCase();
        String cacheKey = turSEInstance.getEndpointUrl() + "/" + normalizedIndex;
        try {
            TurElasticsearchInstance instance = instanceCache.computeIfAbsent(cacheKey, key ->
                    createInstance(turSEInstance.getEndpointUrl(), normalizedIndex));
            return Optional.ofNullable(instance);
        } catch (Exception e) {
            log.error("Error obtaining Elasticsearch instance for index '{}' at '{}'",
                    index, turSEInstance.getEndpointUrl(), e);
            return Optional.empty();
        }
    }

    private TurElasticsearchInstance createInstance(String urlString, String index) {
        try {
            URI uri = URI.create(urlString);
            HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
            RestClient restClient = RestClient.builder(httpHost)
                    .setHttpClientConfigCallback(clientBuilder -> {
                        clientBuilder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                            request.removeHeaders("Content-Type");
                            request.removeHeaders("Accept");
                            request.setHeader("Content-Type", "application/json");
                            request.setHeader("Accept", "application/json");
                        });
                        return clientBuilder;
                    })
                    .build();
            ElasticsearchClient client = new ElasticsearchClient(
                    new RestClientTransport(restClient, new JacksonJsonpMapper()));
            log.info("Connected to Elasticsearch at {} index '{}'", urlString, index);
            return new TurElasticsearchInstance(client, URI.create(urlString).toURL(), index);
        } catch (Exception e) {
            log.error("Error creating Elasticsearch client for '{}': {}", urlString, e.getMessage(), e);
            return null;
        }
    }

    /** Evicts and closes the cached instance for the given endpoint+index. */
    public void evict(String endpointUrl, String index) {
        String cacheKey = endpointUrl + "/" + index;
        TurElasticsearchInstance removed = instanceCache.remove(cacheKey);
        if (removed != null) {
            removed.close();
        }
    }

    @PreDestroy
    public void shutdown() {
        instanceCache.values().forEach(TurElasticsearchInstance::close);
        instanceCache.clear();
    }
}
