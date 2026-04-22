/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.solr;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.solr.source.TurSolrInstanceSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
@Slf4j
public class TurSolrInstanceProcess {
    private final ConcurrentHashMap<String, HttpJdkSolrClient> clientCache = new ConcurrentHashMap<>();
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSolrInstanceSource solrInstanceSource;

    public TurSolrInstanceProcess(
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteRepository turSNSiteRepository,
            TurSolrInstanceSource solrInstanceSource) {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection");
        System.setProperty("jdk.httpclient.HttpClient.version", "HTTP_1_1");
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.solrInstanceSource = solrInstanceSource;
    }

    private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
        String baseUrl = resolveEndpoint(turSEInstance);
        if (baseUrl == null || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
            log.debug("Skipping Solr client init for non-HTTP endpoint: {}", baseUrl);
            return Optional.empty();
        }
        HttpJdkSolrClient httpSolrClient = clientCache.computeIfAbsent(baseUrl, url -> {
            log.info("Starting Solr connection pool for URL: {}", url);
            return new HttpJdkSolrClient.Builder(url)
                    .useHttp1_1(true)
                    .withConnectionTimeout(5000, TimeUnit.MILLISECONDS)
                    .withIdleTimeout(15000, TimeUnit.MILLISECONDS)
                    .build();
        });

        try {
            TurSolrInstance solrInstance = new TurSolrInstance(httpSolrClient, URI.create(baseUrl).toURL(), core);
            return Optional.of(solrInstance);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

        return Optional.empty();
    }

    public Optional<TurSolrInstance> initSolrInstance(String siteName, Locale locale) {
        return turSNSiteRepository.findByNameIgnoreCase(siteName).flatMap(turSNSite -> this.initSolrInstance(turSNSite, locale));

    }

    private Optional<TurSolrInstance> initSolrInstance(TurSNSite turSNSite, Locale locale) {
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
        if (turSNSiteLocale != null) {
            return this.initSolrInstance(turSNSiteLocale);
        } else {
            log.warn("{} site with {} locale not found", turSNSite.getName(), locale);
            return Optional.empty();
        }
    }

    public Optional<TurSolrInstance> initSolrInstance(TurSEInstance turSEInstance, String core) {
        return this.getSolrClient(turSEInstance, core);
    }

    public Optional<TurSolrInstance> initSolrInstance(TurSNSiteLocale turSNSiteLocale) {
        return this.getSolrClient(turSNSiteLocale.getTurSNSite(), turSNSiteLocale);

    }

    private Optional<TurSolrInstance> getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
        return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
    }

    private String resolveEndpoint(TurSEInstance turSEInstance) {
        if (solrInstanceSource != null && solrInstanceSource.isReadOnly()) {
            String configured = solrInstanceSource.getConfiguredEndpoint();
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        return turSEInstance != null ? turSEInstance.getEndpointUrl() : null;
    }

    @PreDestroy
    public void shutdown() {
        clientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                /* ignore */ }
        });
    }
}