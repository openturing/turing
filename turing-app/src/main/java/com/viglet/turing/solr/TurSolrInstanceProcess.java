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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.properties.TurConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Slf4j
@Component
public class TurSolrInstanceProcess {
    private final TurConfigVarRepository turConfigVarRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSolrCache turSolrCache;
    private final CloseableHttpClient closeableHttpClient;
    private final TurConfigProperties turConfigProperties;
    @Inject
    public TurSolrInstanceProcess(TurConfigVarRepository turConfigVarRepository,
                                  TurSEInstanceRepository turSEInstanceRepository,
                                  TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                                  TurSNSiteRepository turSNSiteRepository,
                                  TurSolrCache turSolrCache, CloseableHttpClient closeableHttpClient,
                                  TurConfigProperties turConfigProperties) {
        this.turConfigVarRepository = turConfigVarRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrCache = turSolrCache;
        this.closeableHttpClient = closeableHttpClient;
        this.turConfigProperties = turConfigProperties;
    }

    private Optional<TurSolrInstance> getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
        return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
    }

    private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
        String urlString = String.format("http://%s:%s/solr/%s", turSEInstance.getHost(), turSEInstance.getPort(),
                core);
        if (turSolrCache.isSolrCoreExists(urlString)) {
            HttpSolrClient httpSolrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
                    .withConnectionTimeout(turConfigProperties.getSolr().getTimeout())
                    .withSocketTimeout(turConfigProperties.getSolr().getTimeout()).build();
            try {
                return Optional.of(new TurSolrInstance(closeableHttpClient, httpSolrClient, new URL(urlString), core));
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    public Optional<TurSolrInstance> initSolrInstance(String siteName, String locale) {
        return turSNSiteRepository.findByName(siteName).flatMap(turSNSite -> this.initSolrInstance(turSNSite, locale));

    }

    private Optional<TurSolrInstance> initSolrInstance(TurSNSite turSNSite, String locale) {
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

    public Optional<TurSolrInstance> initSolrInstance() {
        return turConfigVarRepository.findById("DEFAULT_SE")
                .flatMap(turConfigVar -> turSEInstanceRepository.findById(turConfigVar.getValue())
                        .map(turSEInstance -> getSolrClient(turSEInstance, "turing")))
                .orElse(turSEInstanceRepository.findAll().stream().findFirst().map(turSEInstance ->
                        getSolrClient(turSEInstance, "turing")).orElse(null));
    }

}
