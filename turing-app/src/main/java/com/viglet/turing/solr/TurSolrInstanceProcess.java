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
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Slf4j
@Component
public class TurSolrInstanceProcess {
    public static final String TURING = "turing";
    public static final String DEFAULT_SE = "DEFAULT_SE";
    private final TurConfigVarRepository turConfigVarRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSolrCache turSolrCache;
    private final Http2SolrClient http2SolrClient;

    @Inject
    public TurSolrInstanceProcess(TurConfigVarRepository turConfigVarRepository,
                                  TurSEInstanceRepository turSEInstanceRepository,
                                  TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                                  TurSNSiteRepository turSNSiteRepository,
                                  TurSolrCache turSolrCache,
                                  TurConfigProperties turConfigProperties) {
        this.turConfigVarRepository = turConfigVarRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrCache = turSolrCache;
        this.http2SolrClient = new Http2SolrClient.Builder()
                .withConnectionTimeout(turConfigProperties.getSolr().getTimeout(), TimeUnit.MILLISECONDS)
                .withRequestTimeout(turConfigProperties.getSolr().getTimeout(), TimeUnit.MILLISECONDS).build();
    }

    private Optional<TurSolrInstance> getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
        return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
    }

    private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
        String urlString = String.format("%s/solr/%s", turSEInstance.getUrl(),
                core);
        if (turSolrCache.isSolrCoreExists(urlString)) {
            Http2SolrClient httpSolrClient = new Http2SolrClient.Builder(urlString).withHttpClient(http2SolrClient)
                    .build();
            try {
                return Optional.of(new TurSolrInstance(http2SolrClient, httpSolrClient, URI.create(urlString).toURL(), core));
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    public Optional<TurSolrInstance> initSolrInstance(String siteName, Locale locale) {
        return turSNSiteRepository.findByName(siteName).flatMap(turSNSite -> this.initSolrInstance(turSNSite, locale));

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

    public Optional<TurSolrInstance> initSolrInstance() {
        return turConfigVarRepository
                .findById(DEFAULT_SE)
                .flatMap(turConfigVar -> turSEInstanceRepository
                        .findById(turConfigVar.getValue())
                        .map(turSEInstance -> getSolrClient(turSEInstance, TURING)))
                .orElse(turSEInstanceRepository
                        .findAll().stream().findFirst()
                        .map(turSEInstance ->
                                getSolrClient(turSEInstance, TURING))
                        .orElse(null));
    }

}
