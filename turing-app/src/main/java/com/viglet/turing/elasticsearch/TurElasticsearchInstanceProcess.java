/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch instance process for initializing connections
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurElasticsearchInstanceProcess {
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;

    public TurElasticsearchInstanceProcess(
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteRepository turSNSiteRepository) {
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
    }

    private Optional<TurElasticsearchInstance> getElasticsearchClient(TurSNSite turSNSite,
            TurSNSiteLocale turSNSiteLocale) {
        return getElasticsearchClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
    }

    private Optional<TurElasticsearchInstance> getElasticsearchClient(TurSEInstance turSEInstance, String index) {
        String urlString = turSEInstance.getEndpointUrl();

        try {
            RestClient restClient = RestClient.builder(urlString).build();

            RestClientTransport transport = new RestClientTransport(
                    restClient, new JacksonJsonpMapper());

            ElasticsearchClient client = new ElasticsearchClient(transport);

            return Optional.of(new TurElasticsearchInstance(client, URI.create(urlString).toURL(), index));
        } catch (MalformedURLException e) {
            log.error("Error creating Elasticsearch URL: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error initializing Elasticsearch client: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<TurElasticsearchInstance> initElasticsearchInstance(String siteName, Locale locale) {
        return turSNSiteRepository.findByName(siteName)
                .flatMap(turSNSite -> this.initElasticsearchInstance(turSNSite, locale));
    }

    private Optional<TurElasticsearchInstance> initElasticsearchInstance(TurSNSite turSNSite, Locale locale) {
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
        if (turSNSiteLocale != null) {
            return getElasticsearchClient(turSNSite, turSNSiteLocale);
        }
        return Optional.empty();
    }
}
