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
package com.viglet.turing.plugins.se.solr;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Solr implementation of the search engine plugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurSolrSearchEnginePlugin implements TurSearchEnginePlugin {

    private final TurSolr turSolr;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurConfigProperties turConfigProperties;
    private final ResourceLoader resourceLoader;

    public TurSolrSearchEnginePlugin(TurSolr turSolr,
            TurSolrInstanceProcess turSolrInstanceProcess,
            TurConfigProperties turConfigProperties,
            ResourceLoader resourceLoader) {
        this.turSolr = turSolr;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turConfigProperties = turConfigProperties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turSolrInstanceProcess
                .initSolrInstance(context.getSiteName(), context.getLocale())
                .flatMap(turSolrInstance -> turSolr.retrieveSolrFromSN(turSolrInstance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        return turSolrInstanceProcess
                .initSolrInstance(context.getSiteName(), context.getLocale())
                .flatMap(turSolrInstance -> turSolr.retrieveFacetSolrFromSN(turSolrInstance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "solr";
    }

    // ---- Index lifecycle -------------------------------------------------

    @Override
    public void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        String configSet = siteLocale.getLanguage().getLanguage();
        String[] locales = {"en", "es", "pt"};
        if (!Arrays.asList(locales).contains(configSet)) {
            configSet = "en";
        }
        URI solrUri = URI.create(seInstance.getEndpointUrl());
        String solrURL = solrUri.getScheme() + "://" + solrUri.getAuthority();
        if (turConfigProperties.getSolr().isCloud()) {
            try {
                TurSolrUtils.createCollection(solrURL, indexName,
                        resourceLoader.getResource(
                                String.format("classpath:solr/configsets/%s.zip", configSet))
                                .getInputStream(),
                        1);
            } catch (IOException e) {
                log.error("Failed to create Solr collection '{}': {}", indexName, e.getMessage(), e);
            }
        } else {
            TurSolrUtils.createCore(solrURL, indexName, configSet);
        }
    }

    @Override
    public void deleteIndex(TurSEInstance seInstance, String indexName) {
        TurSolrUtils.deleteCore(getSolrUrl(seInstance), indexName);
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        TurSolrUtils.clearCore(seInstance, indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        return TurSolrUtils.coreExists(seInstance, indexName);
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        return TurSolrUtils.listCores(seInstance);
    }

    // ---- Schema management -----------------------------------------------

    @Override
    public void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew) {
        TurSolrFieldAction action = isNew ? TurSolrFieldAction.ADD : TurSolrFieldAction.REPLACE;
        TurSolrUtils.addOrUpdateField(action, seInstance, indexName, fieldName, fieldType, stored, multiValued);
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        TurSolrUtils.deleteField(seInstance, indexName, fieldName, fieldType);
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return TurSolrUtils.existsField(seInstance, indexName, fieldName);
    }

    // ---- Document operations ---------------------------------------------

    @Override
    public boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), locale)
                .map(turSolrInstance -> {
                    turSolr.indexing(turSolrInstance, turSNSite, attributes);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndex(TurSNSite turSNSite, Locale locale, String id) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), locale)
                .map(turSolrInstance -> {
                    turSolr.deIndexing(turSolrInstance, id);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), locale)
                .map(turSolrInstance -> {
                    turSolr.deIndexingByType(turSolrInstance, type);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean commit(TurSNSite turSNSite, Locale locale) {
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), locale)
                .map(turSolr::commit).orElse(false);
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteLocale)
                .map(turSolr::getDocumentTotal).orElse(0L);
    }

    // ---- Helpers ---------------------------------------------------------

    private static String getSolrUrl(TurSEInstance seInstance) {
        try {
            URI uri = URI.create(seInstance.getEndpointUrl());
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return seInstance.getEndpointUrl();
        }
    }
}
