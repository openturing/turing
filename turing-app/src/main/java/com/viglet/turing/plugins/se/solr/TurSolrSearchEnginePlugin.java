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
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSESynonymApplyResult;
import com.viglet.turing.plugins.se.TurSESynonymRule;
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

    // --- S1192: extracted duplicated literals ---
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String ASSET_ID = "assetId";
    private static final String SCORE = "score";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String LABEL = "label";
    private static final String STATUS = "status";


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

    // ---- Synonyms (T663 / §XXXIX, Block AP) ------------------------------

    /**
     * Base name for the per-locale Solr Managed Synonyms resource. The query
     * analyzer in the configset must reference it via
     * {@code ManagedSynonymGraphFilterFactory managed="turing_<locale>"} for the
     * push to take effect at query time.
     */
    private static final String SYNONYM_RESOURCE_BASE = "turing";

    @Override
    public boolean supportsSynonyms() {
        return true;
    }

    @Override
    public TurSESynonymApplyResult applySynonyms(TurSEInstance seInstance, String indexName,
            Locale locale, List<TurSESynonymRule> rules) {
        TurSolrSynonymPayload payload = TurSolrManagedSynonymPayloadBuilder.build(rules);
        List<String> warnings = new ArrayList<>(payload.warnings());
        int applied = payload.appliedRules();

        if (!payload.mappings().isEmpty()) {
            String resource = TurSolrManagedSynonymPayloadBuilder.resourceForLocale(
                    SYNONYM_RESOURCE_BASE, locale);
            TurSEInstance effective = effective(seInstance);
            boolean pushed = TurSolrUtils.putManagedSynonyms(effective, indexName, resource,
                    payload.mappings());
            if (pushed) {
                // Managed-resource edits need a core reload to take effect.
                TurSolrUtils.reloadCore(effective, indexName);
            } else {
                warnings.add("Solr rejected the managed-synonyms update for core '" + indexName
                        + "' (resource '" + resource + "'); rules were not applied. Ensure the "
                        + "query analyzer uses ManagedSynonymGraphFilterFactory managed=\"" + resource
                        + "\".");
                applied = 0;
            }
        }
        return new TurSESynonymApplyResult(true, applied, payload.unsupportedTypes(), warnings);
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
                        solrQuery.setFields("id", "content", ASSET_ID, CHUNK_INDEX,
                                SOURCE_FILE, SCORE);

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
                            Object scoreVal = doc.getFirstValue(SCORE);
                            double score = scoreVal instanceof Number n ? n.doubleValue() : 0.0;
                            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                            putIfPresent(metadata, ASSET_ID, doc.getFirstValue(ASSET_ID));
                            putIfPresent(metadata, CHUNK_INDEX, doc.getFirstValue(CHUNK_INDEX));
                            putIfPresent(metadata, SOURCE_FILE, doc.getFirstValue(SOURCE_FILE));
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
            solrQuery.setFields("id", LABEL, SCORE);
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
            query.setFields("id", LABEL, SCORE);
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
            Object labelVal = doc.getFirstValue(LABEL);
            String label = labelVal == null ? String.valueOf(doc.getFirstValue("id")) : labelVal.toString();
            Object scoreVal = doc.getFirstValue(SCORE);
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

    // ---- Similar documents (T384) ----------------------------------------

    /** Standard fields fetched for similar-document hydration / seed content. */
    private static final List<String> SIMILAR_FIELDS = List.of(
            TurSNFieldName.ID, TurSNFieldName.TITLE, TurSNFieldName.TYPE, TurSNFieldName.URL,
            TurSNFieldName.ABSTRACT, TurSNFieldName.TEXT);
    /** Fields MLT scores against when finding similar documents. */
    private static final String SIMILAR_MLT_FIELDS = String.join(",",
            TurSNFieldName.TITLE, TurSNFieldName.ABSTRACT, TurSNFieldName.TEXT);
    /** Cap the seed text fed to the BM25 fallback so a huge document can't blow up the query. */
    private static final int SIMILAR_MAX_SEED_CHARS = 1_000;

    @Override
    public List<TurSESimilarResult> getSimilarDocuments(TurSNSiteLocale siteLocale, String id, int rows) {
        if (id == null || id.isBlank() || rows < 1) {
            return List.of();
        }
        return turSolrInstanceProcess.initSolrInstance(siteLocale)
                .map(instance -> {
                    List<TurSESimilarResult> mltHits = trySimilarMltHandler(instance, id, rows);
                    if (!mltHits.isEmpty()) {
                        return mltHits;
                    }
                    return trySimilarBm25Fallback(instance, id, rows);
                })
                .orElse(List.of());
    }

    /** Solr {@code /mlt} handler keyed by {@code id:<id>} — the proper MoreLikeThis path. */
    private List<TurSESimilarResult> trySimilarMltHandler(
            com.viglet.turing.solr.TurSolrInstance instance, String id, int rows) {
        try {
            SolrQuery query = new SolrQuery();
            query.set("qt", "/mlt");
            query.setQuery(TurSNFieldName.ID + ":" + escapeSolr(id));
            query.set("mlt.fl", SIMILAR_MLT_FIELDS);
            query.set("mlt.mintf", "1");
            query.set("mlt.mindf", "1");
            query.set("mlt.minwl", "3");
            query.set("mlt.maxqt", "40");
            query.setFields(TurSNFieldName.ID, TurSNFieldName.TITLE, TurSNFieldName.TYPE,
                    TurSNFieldName.URL);
            query.setRows(rows + 1);
            return TurSolr.executeSolrQuery(instance, query)
                    .map(response -> toSimilarResults(response.getResults(), id, rows))
                    .orElse(List.of());
        } catch (Exception e) {
            log.debug("[Solr] /mlt similar handler unavailable ('{}') — falling back to BM25",
                    e.getMessage());
            return List.of();
        }
    }

    /** When the {@code /mlt} handler isn't reachable, emulate MLT with a BM25 disjunction over the seed text. */
    private List<TurSESimilarResult> trySimilarBm25Fallback(
            com.viglet.turing.solr.TurSolrInstance instance, String id, int rows) {
        List<Map<String, Object>> seed = fetchByIds(instance, List.of(id));
        if (seed.isEmpty()) {
            return List.of();
        }
        String text = similarityText(seed.get(0));
        if (text.isBlank()) {
            return List.of();
        }
        try {
            String escaped = escapeSolr(text);
            String q = "%s:(%s) OR %s:(%s) OR %s:(%s)".formatted(
                    TurSNFieldName.TITLE, escaped, TurSNFieldName.ABSTRACT, escaped,
                    TurSNFieldName.TEXT, escaped);
            SolrQuery query = new SolrQuery().setQuery(q);
            query.setFields(TurSNFieldName.ID, TurSNFieldName.TITLE, TurSNFieldName.TYPE,
                    TurSNFieldName.URL);
            query.setRows(rows + 1);
            return TurSolr.executeSolrQuery(instance, query)
                    .map(response -> toSimilarResults(response.getResults(), id, rows))
                    .orElse(List.of());
        } catch (Exception e) {
            log.warn("[Solr] similar BM25 fallback for id '{}' failed: {}", id, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getDocumentsByIds(TurSNSiteLocale siteLocale, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return turSolrInstanceProcess.initSolrInstance(siteLocale)
                .map(instance -> fetchByIds(instance, ids))
                .orElse(List.of());
    }

    /** Fetches the standard fields for {@code ids}, preserving input order. */
    private List<Map<String, Object>> fetchByIds(com.viglet.turing.solr.TurSolrInstance instance,
            List<String> ids) {
        String q = ids.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> TurSNFieldName.ID + ":" + escapeSolr(s))
                .collect(java.util.stream.Collectors.joining(" OR "));
        if (q.isEmpty()) {
            return List.of();
        }
        SolrQuery query = new SolrQuery().setQuery(q).setRows(ids.size());
        query.setFields(SIMILAR_FIELDS.toArray(new String[0]));
        return TurSolr.executeSolrQuery(instance, query)
                .map(response -> orderByIds(indexSimilarDocs(response), ids))
                .orElse(List.of());
    }

    /** Indexes the response docs by id, projecting only the SIMILAR_FIELDS (first value of multis). */
    private Map<String, Map<String, Object>> indexSimilarDocs(
            org.apache.solr.client.solrj.response.QueryResponse response) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (org.apache.solr.common.SolrDocument doc : response.getResults()) {
            Map<String, Object> fields = extractSimilarFields(doc);
            Object docId = fields.get(TurSNFieldName.ID);
            if (docId != null) {
                byId.put(docId.toString(), fields);
            }
        }
        return byId;
    }

    private Map<String, Object> extractSimilarFields(org.apache.solr.common.SolrDocument doc) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        for (String field : SIMILAR_FIELDS) {
            Object value = doc.getFieldValue(field);
            if (value instanceof java.util.Collection<?> col) {
                value = col.isEmpty() ? null : col.iterator().next();
            }
            if (value != null) {
                fields.put(field, value);
            }
        }
        return fields;
    }

    /** Re-orders the indexed docs to match the requested id order, dropping misses. */
    private List<Map<String, Object>> orderByIds(Map<String, Map<String, Object>> byId,
            List<String> ids) {
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String wanted : ids) {
            Map<String, Object> fields = byId.get(wanted);
            if (fields != null) {
                ordered.add(fields);
            }
        }
        return ordered;
    }

    /** Builds the seed text (title + abstract + text) used by the BM25 fallback / vector path. */
    private static String similarityText(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : List.of(TurSNFieldName.TITLE, TurSNFieldName.ABSTRACT,
                TurSNFieldName.TEXT)) {
            Object value = fields.get(field);
            if (value != null) {
                String text = value.toString().trim();
                if (!text.isEmpty()) {
                    if (!sb.isEmpty()) {
                        sb.append(' ');
                    }
                    sb.append(text);
                }
            }
        }
        String text = sb.toString().trim();
        return text.length() > SIMILAR_MAX_SEED_CHARS
                ? text.substring(0, SIMILAR_MAX_SEED_CHARS)
                : text;
    }

    private static List<TurSESimilarResult> toSimilarResults(
            org.apache.solr.common.SolrDocumentList docs, String excludeId, int rows) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<TurSESimilarResult> results = new ArrayList<>();
        for (org.apache.solr.common.SolrDocument doc : docs) {
            String docId = firstString(doc, TurSNFieldName.ID);
            if (docId != null && !docId.equals(excludeId)) {
                results.add(TurSESimilarResult.builder()
                        .id(docId)
                        .title(firstString(doc, TurSNFieldName.TITLE))
                        .type(firstString(doc, TurSNFieldName.TYPE))
                        .url(firstString(doc, TurSNFieldName.URL))
                        .build());
                if (results.size() >= rows) {
                    break;
                }
            }
        }
        return results;
    }

    private static String firstString(org.apache.solr.common.SolrDocument doc, String field) {
        Object value = doc.getFirstValue(field);
        return value == null ? null : value.toString();
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
                                    docs.add(extractExportFields(doc, siteFieldMap));
                                }
                                return docs;
                            })
                            .orElse(List.of());
                })
                .orElse(List.of());
    }

    /** Projects one Solr doc into the requested export fields (id/type always), collapsing single-value multis. */
    private Map<String, Object> extractExportFields(org.apache.solr.common.SolrDocument doc,
            Map<String, ?> siteFieldMap) {
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
        return fields;
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

    @Override
    public long getDocumentCountWithField(TurSNSiteLocale turSNSiteLocale, String fieldName) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteLocale)
                .map(instance -> turSolr.getDocumentTotalWithField(instance, fieldName))
                .orElse(-1L);
    }

    @Override
    public long getDocumentCountWithFieldBelow(TurSNSiteLocale turSNSiteLocale, String fieldName,
            int threshold) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteLocale)
                .map(instance -> turSolr.getDocumentTotalWithFieldBelow(instance, fieldName, threshold))
                .orElse(-1L);
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
            info.put(STATUS, "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to retrieve Solr system info: {}", e.getMessage());
            info.put(STATUS, "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve Solr system info: {}", e.getMessage());
            info.put(STATUS, "DOWN");
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

    // ---- Spell check (T686 / §XLI.2, Block AR) ---------------------------

    @Override
    public com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult spellCheck(
            String siteName, String term, Locale locale) {
        return turSolrInstanceProcess.initSolrInstance(siteName, locale)
                .map(instance -> turSolr.spellCheckTerm(instance, term))
                .orElseGet(com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult::new);
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
