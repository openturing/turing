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
package com.viglet.turing.plugins.se.lucene;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.lucene.TurLuceneUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lucene implementation of {@link TurSearchEnginePlugin}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class TurLuceneSearchEnginePlugin implements TurSearchEnginePlugin {

    private final TurLucene turLucene;
    private final TurLuceneInstanceProcess turLuceneInstanceProcess;

    public TurLuceneSearchEnginePlugin(TurLucene turLucene,
            TurLuceneInstanceProcess turLuceneInstanceProcess) {
        this.turLucene = turLucene;
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turLuceneInstanceProcess
                .initLuceneInstance(context.getSiteName(), context.getLocale())
                .flatMap(instance -> turLucene.retrieveLuceneFromSN(instance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        return turLuceneInstanceProcess
                .initLuceneInstance(context.getSiteName(), context.getLocale())
                .flatMap(instance -> turLucene.retrieveFacetLuceneFromSN(instance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "lucene";
    }

    // ---- Index lifecycle -------------------------------------------------

    @Override
    public void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        TurLuceneUtils.createCore(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public void deleteIndex(TurSEInstance seInstance, String indexName) {
        TurLuceneUtils.deleteCore(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        // Lucene: clear by deleting and recreating the index
        TurLuceneUtils.deleteCore(seInstance.getEndpointUrl(), indexName);
        TurLuceneUtils.createCore(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        return TurLuceneUtils.coreExists(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        return TurLuceneUtils.listCores(seInstance.getEndpointUrl());
    }

    // ---- Schema management (schema-less -> no-op) ------------------------

    @Override
    public void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew) {
        // Lucene is schema-less — field types are tracked in the JPA model only
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        // Lucene is schema-less — no schema operation needed
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return false; // Lucene is schema-less
    }

    // ---- Document operations ---------------------------------------------

    @Override
    public boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), locale)
                .map(luceneInstance -> {
                    try {
                        turLucene.indexing(luceneInstance, turSNSite, attributes);
                        return true;
                    } catch (IllegalArgumentException e) {
                        if (e.getMessage() != null && e.getMessage().contains("doc values type")) {
                            log.warn("[Lucene] Schema conflict detected ({}). Recreating index and retrying.",
                                    e.getMessage());
                            turLuceneInstanceProcess.resetInstance(luceneInstance.getIndexPath());
                            return turLuceneInstanceProcess
                                    .initLuceneInstance(turSNSite.getName(), locale)
                                    .map(fresh -> {
                                        turLucene.indexing(fresh, turSNSite, attributes);
                                        return true;
                                    }).orElse(false);
                        }
                        log.error("[Lucene] Indexing failed: {}", e.getMessage(), e);
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public boolean deIndex(TurSNSite turSNSite, Locale locale, String id) {
        return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), locale)
                .map(luceneInstance -> {
                    turLucene.deIndexing(luceneInstance, id);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type) {
        return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), locale)
                .map(luceneInstance -> {
                    turLucene.deIndexingByType(luceneInstance, type);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean commit(TurSNSite turSNSite, Locale locale) {
        // Lucene commits are synchronous — nothing extra needed
        return true;
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        return turLuceneInstanceProcess.initLuceneInstance(turSNSiteLocale)
                .map(instance -> {
                    try {
                        return (long) instance.getWriter().getDocStats().numDocs;
                    } catch (Exception e) {
                        log.warn("[Lucene] Could not get document total: {}", e.getMessage());
                        return 0L;
                    }
                }).orElse(0L);
    }

    // ---- System info -----------------------------------------------------

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        var info = new LinkedHashMap<String, String>();
        info.put("engine", "Apache Lucene (embedded)");
        info.put("status", "UP");
        info.put("version", org.apache.lucene.util.Version.LATEST.toString());
        info.put("indexPath", seInstance.getEndpointUrl());
        return info;
    }
}
