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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.solr.client.solrj.request.SolrQuery;

/**
 * Solr implementation of the search engine plugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurSolrSearchEnginePlugin implements TurSearchEnginePlugin {

    private static final String VERSION_KEY = "version";

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
        String solrUrl = getSolrUrl(seInstance);
        if (turConfigProperties.getSolr().isCloud()) {
            TurSolrUtils.deleteCollection(solrUrl, indexName);
        } else {
            TurSolrUtils.deleteCore(solrUrl, indexName);
        }
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        TurSolrUtils.clearCore(seInstance, indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        if (turConfigProperties.getSolr().isCloud()) {
            return TurSolrUtils.collectionExists(seInstance, indexName);
        }
        return TurSolrUtils.coreExists(seInstance, indexName);
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        if (turConfigProperties.getSolr().isCloud()) {
            return TurSolrUtils.listCollections(seInstance);
        }
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

    // ---- Content retrieval ------------------------------------------------

    private static final java.util.Set<String> ALWAYS_EXPORT_FIELDS = java.util.Set.of("id", "type");

    @Override
    public List<Map<String, Object>> retrieveDocumentPage(TurSNSiteLocale turSNSiteLocale,
            Map<String, ?> siteFieldMap, int start, int rows) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteLocale)
                .map(instance -> {
                    SolrQuery query = new SolrQuery()
                            .setQuery("*:*")
                            .setStart(start)
                            .setRows(rows)
                            .addSort("id", SolrQuery.ORDER.asc);
                    return TurSolr.executeSolrQuery(instance, query)
                            .map(response -> {
                                List<Map<String, Object>> docs = new ArrayList<>();
                                for (var doc : response.getResults()) {
                                    Map<String, Object> fields = new HashMap<>();
                                    for (String fieldName : doc.getFieldNames()) {
                                        if (!ALWAYS_EXPORT_FIELDS.contains(fieldName)
                                                && !siteFieldMap.containsKey(fieldName)) {
                                            continue;
                                        }
                                        Object value = doc.getFieldValue(fieldName);
                                        boolean isMultiValued = isFieldMultiValued(siteFieldMap, fieldName);
                                        if (!isMultiValued && value instanceof java.util.Collection<?> col) {
                                            value = col.isEmpty() ? null : col.iterator().next();
                                        }
                                        if (value != null) {
                                            fields.put(fieldName, value);
                                        }
                                    }
                                    docs.add(fields);
                                }
                                return docs;
                            })
                            .orElse(List.of());
                })
                .orElse(List.of());
    }

    private static boolean isFieldMultiValued(Map<String, ?> siteFieldMap, String fieldName) {
        Object field = siteFieldMap.get(fieldName);
        if (field instanceof com.viglet.turing.persistence.model.sn.field.TurSNSiteField f) {
            return f.getMultiValued() == 1;
        }
        return false;
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteLocale)
                .map(turSolr::getDocumentTotal).orElse(0L);
    }

    // ---- Copy field ------------------------------------------------------

    @Override
    public void createCopyField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean multiValued) {
        if (TurSolrUtils.isCreateCopyFieldByCore(seInstance, indexName, fieldName, fieldType)) {
            TurSolrUtils.createCopyFieldByCore(seInstance, indexName, fieldName, multiValued);
        }
    }

    // ---- System info -----------------------------------------------------

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        var info = new java.util.LinkedHashMap<String, String>();
        info.put("engine", "Apache Solr");
        try {
            String solrUrl = getSolrUrl(seInstance);
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(solrUrl + "/solr/admin/info/system"))
                    .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String json = response.body();
            if (json != null) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(json);
                var lucene = root.path("lucene");
                if (!lucene.isMissingNode()) {
                    info.put(VERSION_KEY, lucene.path("solr-spec-version").asText(""));
                    info.put("luceneVersion", lucene.path("lucene-spec-version").asText(""));
                }
                var system = root.path("system");
                if (!system.isMissingNode()) {
                    info.put("os", system.path("name").asText("") + " "
                            + system.path(VERSION_KEY).asText(""));
                }
                var jvm = root.path("jvm");
                if (!jvm.isMissingNode()) {
                    info.put("javaVersion", jvm.path(VERSION_KEY).asText(""));
                    info.put("jvmMemory", jvm.path("memory").path("raw").path("used").asText(""));
                }
            }
            info.put("status", "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to retrieve Solr system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve Solr system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
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
