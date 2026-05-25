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
import com.viglet.turing.solr.source.TurSolrInstanceSource;
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
    private final TurSolrInstanceSource solrInstanceSource;

    public TurSolrSearchEnginePlugin(TurSolr turSolr,
            TurSolrInstanceProcess turSolrInstanceProcess,
            TurConfigProperties turConfigProperties,
            ResourceLoader resourceLoader,
            TurSolrInstanceSource solrInstanceSource) {
        this.turSolr = turSolr;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turConfigProperties = turConfigProperties;
        this.resourceLoader = resourceLoader;
        this.solrInstanceSource = solrInstanceSource;
    }

    private TurSEInstance effective(TurSEInstance original) {
        if (solrInstanceSource == null || !solrInstanceSource.isReadOnly()) {
            return original;
        }
        String endpoint = solrInstanceSource.getConfiguredEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return original;
        }
        if (original != null && endpoint.equals(original.getEndpointUrl())) {
            return original;
        }
        TurSEInstance override = new TurSEInstance();
        if (original != null) {
            override.setId(original.getId());
            override.setTitle(original.getTitle());
            override.setDescription(original.getDescription());
            override.setIcon(original.getIcon());
            override.setEnabled(original.getEnabled());
            override.setTurSEVendor(original.getTurSEVendor());
        }
        override.setEndpointUrl(endpoint);
        return override;
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
        seInstance = effective(seInstance);
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
        seInstance = effective(seInstance);
        String solrUrl = getSolrUrl(seInstance);
        if (turConfigProperties.getSolr().isCloud()) {
            TurSolrUtils.deleteCollection(solrUrl, indexName);
        } else {
            TurSolrUtils.deleteCore(solrUrl, indexName);
        }
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        TurSolrUtils.clearCore(effective(seInstance), indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        seInstance = effective(seInstance);
        if (turConfigProperties.getSolr().isCloud()) {
            return TurSolrUtils.collectionExists(seInstance, indexName);
        }
        return TurSolrUtils.coreExists(seInstance, indexName);
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        seInstance = effective(seInstance);
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
        TurSolrUtils.addOrUpdateField(action, effective(seInstance), indexName, fieldName, fieldType,
                stored, multiValued);
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        TurSolrUtils.deleteField(effective(seInstance), indexName, fieldName, fieldType);
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return TurSolrUtils.existsField(effective(seInstance), indexName, fieldName);
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
    public int indexDocuments(TurSNSite turSNSite, Locale locale,
            List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), locale)
                .map(turSolrInstance -> turSolr.indexingBatch(turSolrInstance, turSNSite, documents))
                .orElse(0);
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

    // ---- T24b / §III.2 standalone (RAG BM25 cores) -----------------------

    /**
     * T24b — pushes a single {@link Map} body into the named Solr core
     * via {@link org.apache.solr.client.solrj.SolrClient#add}. Bypasses
     * the SN-site-driven {@link TurSolr#indexing} path because RAG cores
     * have a simple, fixed schema (id + content + locale + assetId +
     * chunkIndex + sourceFile) and don't need field-type / multi-value
     * normalization.
     */
    @Override
    public boolean indexStandaloneDocument(TurSEInstance seInstance, String indexName,
            Map<String, Object> attributes) {
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    try {
                        org.apache.solr.common.SolrInputDocument doc =
                                new org.apache.solr.common.SolrInputDocument();
                        attributes.forEach(doc::addField);
                        instance.getSolrClient().add(doc);
                        return true;
                    } catch (Exception e) {
                        log.warn("[Solr] standalone index '{}' failed: {}", indexName, e.getMessage());
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public int indexStandaloneDocuments(TurSEInstance seInstance, String indexName,
            List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        // Solr accepts a Collection<SolrInputDocument> in a single add call
        // — one HTTP round-trip for the whole batch instead of N.
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    java.util.List<org.apache.solr.common.SolrInputDocument> batch =
                            new java.util.ArrayList<>(documents.size());
                    for (Map<String, Object> attrs : documents) {
                        org.apache.solr.common.SolrInputDocument doc =
                                new org.apache.solr.common.SolrInputDocument();
                        attrs.forEach(doc::addField);
                        batch.add(doc);
                    }
                    try {
                        instance.getSolrClient().add(batch);
                        return batch.size();
                    } catch (Exception e) {
                        log.warn("[Solr] standalone batch index '{}' failed: {}",
                                indexName, e.getMessage());
                        return 0;
                    }
                }).orElse(0);
    }

    @Override
    public boolean deIndexStandalone(TurSEInstance seInstance, String indexName, String id) {
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    try {
                        instance.getSolrClient().deleteById(id);
                        return true;
                    } catch (Exception e) {
                        log.warn("[Solr] standalone delete id='{}' from '{}' failed: {}",
                                id, indexName, e.getMessage());
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public boolean deIndexStandaloneByField(TurSEInstance seInstance, String indexName,
            String fieldName, String fieldValue) {
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    try {
                        // Field value is quoted so multi-token values
                        // (e.g. file paths with spaces) get matched as
                        // a phrase, not OR'd across tokens.
                        instance.getSolrClient().deleteByQuery(
                                fieldName + ":\"" + fieldValue + "\"");
                        return true;
                    } catch (Exception e) {
                        log.warn("[Solr] standalone delete-by-field {}='{}' on '{}' failed: {}",
                                fieldName, fieldValue, indexName, e.getMessage());
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public boolean commitStandalone(TurSEInstance seInstance, String indexName) {
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(turSolr::commit).orElse(false);
    }

    @Override
    public List<com.viglet.turing.plugins.se.TurSEStandaloneHit> retrieveStandalone(
            TurSEInstance seInstance, String indexName, String query, int topK) {
        if (query == null || query.isBlank() || topK < 1) {
            return List.of();
        }
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    try {
                        SolrQuery solrQuery = new SolrQuery();
                        // edismax on the canonical text field. Escaping
                        // the raw user input so a stray colon / paren
                        // doesn't blow up the parser — admins of the
                        // ingest side never get to inject syntax here.
                        solrQuery.setQuery("content:(" + escapeSolr(query) + ")");
                        solrQuery.setRows(topK);
                        // Asking for the chunk fields explicitly avoids
                        // shipping the embedding vector (if anyone ever
                        // adds one to the BM25 core by mistake) and keeps
                        // the response small.
                        solrQuery.setFields("id", "content", "assetId", "chunkIndex",
                                "sourceFile", "score");

                        var response = instance.getSolrClient().query(solrQuery);
                        org.apache.solr.common.SolrDocumentList docs = response.getResults();
                        if (docs == null || docs.isEmpty()) {
                            return java.util.List.<com.viglet.turing.plugins.se.TurSEStandaloneHit>of();
                        }
                        java.util.List<com.viglet.turing.plugins.se.TurSEStandaloneHit> hits =
                                new java.util.ArrayList<>(docs.size());
                        for (org.apache.solr.common.SolrDocument doc : docs) {
                            String id = String.valueOf(doc.getFirstValue("id"));
                            Object contentVal = doc.getFirstValue("content");
                            String content = contentVal == null ? "" : contentVal.toString();
                            Object scoreVal = doc.getFirstValue("score");
                            double score = scoreVal instanceof Number n ? n.doubleValue() : 0.0;
                            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                            putIfPresent(metadata, "assetId", doc.getFirstValue("assetId"));
                            putIfPresent(metadata, "chunkIndex", doc.getFirstValue("chunkIndex"));
                            putIfPresent(metadata, "sourceFile", doc.getFirstValue("sourceFile"));
                            hits.add(new com.viglet.turing.plugins.se.TurSEStandaloneHit(
                                    id, content, metadata, score));
                        }
                        return hits;
                    } catch (Exception e) {
                        log.warn("[Solr] standalone retrieve '{}' query='{}' failed: {}",
                                indexName, query, e.getMessage());
                        return java.util.List.<com.viglet.turing.plugins.se.TurSEStandaloneHit>of();
                    }
                }).orElse(List.of());
    }

    /**
     * Escapes Solr's classic-parser special characters in a free-text
     * query so admin input doesn't have to know Lucene syntax.
     */
    private static String escapeSolr(String query) {
        return query.replaceAll("([\\\\+\\-!\\(\\)\\{\\}\\[\\]\\^\"~\\*\\?:/])", "\\\\$1");
    }

    /**
     * T28 / §III.5 — Solr {@code MoreLikeThis} backend for
     * {@code TurSeMltIntentClassifier}. Targets the {@code /mlt} request
     * handler with {@code stream.body=<text>}; Solr 9 disables the
     * stream-body parameter by default for security, so admins enabling
     * the analytics intent path on Solr must opt in via
     * {@code solrconfig.xml}'s {@code <requestParsers enableStreamBody="true"/>}.
     *
     * <p>Falls back to a regular BM25 search against {@code field} when
     * the {@code /mlt} handler returns an empty result OR throws — keeps
     * the analytics classifier alive on installs that haven't enabled
     * stream-body yet (short transcripts still get a reasonable match
     * through plain BM25 over the samples field).
     */
    @Override
    public List<com.viglet.turing.plugins.se.TurSEStandaloneHit> moreLikeThisStandalone(
            TurSEInstance seInstance, String indexName, String field, String text, int topK) {
        if (text == null || text.isBlank() || topK < 1) {
            return List.of();
        }
        return turSolrInstanceProcess.initSolrInstance(effective(seInstance), indexName)
                .map(instance -> {
                    List<com.viglet.turing.plugins.se.TurSEStandaloneHit> mltHits =
                            tryMltHandler(instance, field, text, topK);
                    if (!mltHits.isEmpty()) {
                        return mltHits;
                    }
                    return tryBm25Fallback(instance, indexName, field, text, topK);
                }).orElse(List.of());
    }

    private List<com.viglet.turing.plugins.se.TurSEStandaloneHit> tryMltHandler(
            com.viglet.turing.solr.TurSolrInstance instance, String field, String text, int topK) {
        try {
            SolrQuery solrQuery = new SolrQuery();
            // `qt` routes to the named request handler. `setRequestHandler`
            // would do the same but was deprecated in SolrJ 9.
            solrQuery.set("qt", "/mlt");
            solrQuery.set("stream.body", text);
            solrQuery.set("mlt.fl", field);
            solrQuery.set("mlt.mintf", "1");
            solrQuery.set("mlt.mindf", "1");
            solrQuery.set("mlt.minwl", "3");
            solrQuery.set("mlt.maxqt", "40");
            solrQuery.setFields("id", "label", "score");
            solrQuery.setRows(topK);
            var response = instance.getSolrClient().query(solrQuery);
            return collectMltHits(response.getResults());
        } catch (Exception e) {
            log.debug("[Solr] /mlt handler unavailable ('{}') — falling back to BM25",
                    e.getMessage());
            return List.of();
        }
    }

    private List<com.viglet.turing.plugins.se.TurSEStandaloneHit> tryBm25Fallback(
            com.viglet.turing.solr.TurSolrInstance instance, String indexName, String field,
            String text, int topK) {
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery(field + ":(" + escapeSolr(text) + ")");
            query.setFields("id", "label", "score");
            query.setRows(topK);
            var response = instance.getSolrClient().query(query);
            return collectMltHits(response.getResults());
        } catch (Exception e) {
            log.warn("[Solr] MLT BM25 fallback on '{}' failed: {}", indexName, e.getMessage());
            return List.of();
        }
    }

    private static List<com.viglet.turing.plugins.se.TurSEStandaloneHit> collectMltHits(
            org.apache.solr.common.SolrDocumentList docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<com.viglet.turing.plugins.se.TurSEStandaloneHit> hits = new ArrayList<>(docs.size());
        for (org.apache.solr.common.SolrDocument doc : docs) {
            Object labelVal = doc.getFirstValue("label");
            String label = labelVal == null ? String.valueOf(doc.getFirstValue("id")) : labelVal.toString();
            Object scoreVal = doc.getFirstValue("score");
            double score = scoreVal instanceof Number n ? n.doubleValue() : 0.0;
            // The classifier rendering treats the hit "id" as the label.
            // Catalog rows are one per label so this round-trip is lossless.
            hits.add(new com.viglet.turing.plugins.se.TurSEStandaloneHit(
                    label, "", java.util.Map.of(), score));
        }
        return hits;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
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
        seInstance = effective(seInstance);
        if (TurSolrUtils.isCreateCopyFieldByCore(seInstance, indexName, fieldName, fieldType)) {
            TurSolrUtils.createCopyFieldByCore(seInstance, indexName, fieldName, multiValued);
        }
    }

    // ---- System info -----------------------------------------------------

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        seInstance = effective(seInstance);
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

    // ---- Autocomplete -----------------------------------------------------

    @Override
    public List<String> autoComplete(String siteName, String term, Locale locale, long rows) {
        return turSolrInstanceProcess.initSolrInstance(siteName, locale)
                .map(instance -> {
                    try {
                        var response = turSolr.autoComplete(instance, term);
                        if (response != null && response.getSuggestions() != null
                                && !response.getSuggestions().isEmpty()) {
                            return response.getSuggestions().getFirst().getAlternatives();
                        }
                    } catch (Exception e) {
                        log.error("Solr autocomplete error: {}", e.getMessage(), e);
                    }
                    return List.<String>of();
                }).orElse(List.of());
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
