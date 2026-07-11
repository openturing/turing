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
package com.viglet.turing.plugins.se.elasticsearch;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstance;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.elasticsearch.TurElasticsearchUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSESynonymApplyResult;
import com.viglet.turing.plugins.se.TurSESynonymRule;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Elasticsearch implementation of the search engine plugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurElasticsearchSearchEnginePlugin implements TurSearchEnginePlugin {

    // --- S1192: extracted duplicated literals ---
    private static final String CONTENT = "content";
    private static final String ASSET_ID = "assetId";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String LABEL = "label";
    private static final String STATUS = "status";


    private final TurElasticsearch turElasticsearch;
    private final TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurElasticsearchSearchEnginePlugin(
            TurElasticsearch turElasticsearch,
            TurElasticsearchInstanceProcess turElasticsearchInstanceProcess,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turElasticsearch = turElasticsearch;
        this.turElasticsearchInstanceProcess = turElasticsearchInstanceProcess;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveElasticsearchFromSN(elasticsearchInstance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveFacetElasticsearchFromSN(
                        elasticsearchInstance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "elasticsearch";
    }

    // ---- Index lifecycle -------------------------------------------------

    @Override
    public void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        Map<String, TurSEFieldType> resolved = fieldTypes.isEmpty()
                ? buildFieldTypesFromSite(siteLocale)
                : fieldTypes;
        TurElasticsearchUtils.createIndex(seInstance.getEndpointUrl(), indexName, resolved);
    }

    private Map<String, TurSEFieldType> buildFieldTypesFromSite(TurSNSiteLocale siteLocale) {
        if (siteLocale == null || siteLocale.getTurSNSite() == null) {
            return Collections.emptyMap();
        }
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(siteLocale.getTurSNSite(), 1)
                .stream()
                .filter(f -> f.getType() != null)
                .collect(Collectors.toMap(
                        TurSNSiteFieldExt::getName,
                        TurSNSiteFieldExt::getType,
                        (a, b) -> a));
    }

    @Override
    public void deleteIndex(TurSEInstance seInstance, String indexName) {
        TurElasticsearchUtils.deleteIndex(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        // ES: delete and recreate the index
        TurElasticsearchUtils.deleteIndex(seInstance.getEndpointUrl(), indexName);
        TurElasticsearchUtils.createIndex(seInstance.getEndpointUrl(), indexName);
    }

    @Override
    public boolean indexExists(TurSEInstance seInstance, String indexName) {
        // ES auto-creates indices on first index
        return true;
    }

    @Override
    public List<TurSECoreInfo> listIndexes(TurSEInstance seInstance) {
        return TurElasticsearchUtils.listIndexes(seInstance.getEndpointUrl());
    }

    // ---- Schema management (schema-less -> no-op) ------------------------

    @Override
    public void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew) {
        // Elasticsearch is schema-less — field types are tracked in the JPA model only
    }

    @Override
    public void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType) {
        // Elasticsearch is schema-less — no schema operation needed
    }

    @Override
    public boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName) {
        return false; // Elasticsearch is schema-less
    }

    // ---- Synonyms (T664 / §XXXIX, Block AP) ------------------------------

    /**
     * Base name for the per-locale ES synonyms set. The index's search analyzer
     * must reference it via a {@code synonym_graph} filter with
     * {@code synonyms_set="turing_<locale>"} and {@code updateable: true} for the
     * push to take effect at query time.
     */
    private static final String SYNONYM_SET_BASE = "turing";

    @Override
    public boolean supportsSynonyms() {
        return true;
    }

    @Override
    public TurSESynonymApplyResult applySynonyms(TurSEInstance seInstance, String indexName,
            Locale locale, List<TurSESynonymRule> rules) {
        TurElasticsearchSynonymRules built = TurElasticsearchSynonymRulesBuilder.build(rules);
        List<String> warnings = new ArrayList<>(built.warnings());
        int applied = built.appliedRules();

        if (!built.ruleLines().isEmpty()) {
            String setId = TurElasticsearchSynonymRulesBuilder.synonymSetId(SYNONYM_SET_BASE, locale);
            boolean pushed = TurElasticsearchUtils.putSynonymSet(seInstance.getEndpointUrl(), setId,
                    built.ruleLines());
            if (!pushed) {
                warnings.add("Elasticsearch rejected the synonyms set '" + setId + "'; rules were not "
                        + "applied. Ensure the index's search analyzer uses a synonym_graph filter with "
                        + "synonyms_set=\"" + setId + "\" and updateable:true.");
                applied = 0;
            }
        }
        return new TurSESynonymApplyResult(true, applied, built.unsupportedTypes(), warnings);
    }

    // ---- Document operations ---------------------------------------------

    @Override
    public boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.indexing(esInstance, turSNSite, attributes);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndex(TurSNSite turSNSite, Locale locale, String id) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.deIndexing(esInstance, id);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(turSNSite.getName(), locale)
                .map(esInstance -> {
                    turElasticsearch.deIndexingByType(esInstance, type);
                    return true;
                }).orElse(false);
    }

    @Override
    public boolean commit(TurSNSite turSNSite, Locale locale) {
        // Elasticsearch commits are synchronous — nothing extra needed
        return true;
    }

    // ---- T24b / §III.2 standalone (RAG BM25 cores) -----------------------

    @Override
    public boolean indexStandaloneDocument(TurSEInstance seInstance, String indexName,
            Map<String, Object> attributes) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    String docId = java.util.Optional.ofNullable(attributes.get("id"))
                            .map(Object::toString).orElse(null);
                    if (docId == null) {
                        log.warn("[ES] standalone index '{}' missing id — dropping doc", indexName);
                        return false;
                    }
                    try {
                        co.elastic.clients.elasticsearch.core.IndexRequest<Map<String, Object>> req =
                                co.elastic.clients.elasticsearch.core.IndexRequest.of(r -> r
                                        .index(indexName)
                                        .id(docId)
                                        .document(attributes));
                        instance.getClient().index(req);
                        return true;
                    } catch (Exception e) {
                        log.warn("[ES] standalone index '{}' id='{}' failed: {}",
                                indexName, docId, e.getMessage());
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
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    co.elastic.clients.elasticsearch.core.BulkRequest.Builder bulk =
                            new co.elastic.clients.elasticsearch.core.BulkRequest.Builder();
                    int queued = queueBulkOperations(bulk, indexName, documents);
                    if (queued == 0) {
                        return 0;
                    }
                    try {
                        co.elastic.clients.elasticsearch.core.BulkResponse resp =
                                instance.getClient().bulk(bulk.build());
                        // The bulk API may partially succeed — only count items
                        // without an error. resp.errors() is the global flag;
                        // the per-item check is more precise.
                        return resp.errors() ? countBulkSuccesses(resp, indexName) : queued;
                    } catch (Exception e) {
                        log.warn("[ES] bulk standalone '{}' failed: {}", indexName, e.getMessage());
                        return 0;
                    }
                }).orElse(0);
    }

    /** Queues an index op per document with a non-null id, returning the count queued. */
    private int queueBulkOperations(co.elastic.clients.elasticsearch.core.BulkRequest.Builder bulk,
            String indexName, List<Map<String, Object>> documents) {
        int queued = 0;
        for (Map<String, Object> attrs : documents) {
            String docId = java.util.Optional.ofNullable(attrs.get("id"))
                    .map(Object::toString).orElse(null);
            if (docId == null) {
                log.warn("[ES] bulk standalone '{}': skipping doc with no id", indexName);
                continue;
            }
            final String id = docId;
            bulk.operations(op -> op.index(idx -> idx
                    .index(indexName).id(id).document(attrs)));
            queued++;
        }
        return queued;
    }

    /** Counts the bulk items that succeeded, logging each per-item failure. */
    private int countBulkSuccesses(co.elastic.clients.elasticsearch.core.BulkResponse resp,
            String indexName) {
        int ok = 0;
        for (var item : resp.items()) {
            co.elastic.clients.elasticsearch._types.ErrorCause itemError = item.error();
            if (itemError == null) {
                ok++;
            } else {
                log.warn("[ES] bulk standalone '{}': item id='{}' failed: {}",
                        indexName, item.id(), itemError.reason());
            }
        }
        return ok;
    }

    @Override
    public boolean deIndexStandalone(TurSEInstance seInstance, String indexName, String id) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    try {
                        instance.getClient().delete(d -> d.index(indexName).id(id));
                        return true;
                    } catch (Exception e) {
                        log.warn("[ES] standalone delete id='{}' from '{}' failed: {}",
                                id, indexName, e.getMessage());
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public boolean deIndexStandaloneByField(TurSEInstance seInstance, String indexName,
            String fieldName, String fieldValue) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    try {
                        instance.getClient().deleteByQuery(d -> d
                                .index(indexName)
                                .query(q -> q.term(t -> t
                                        .field(fieldName)
                                        .value(co.elastic.clients.elasticsearch._types.FieldValue.of(fieldValue)))));
                        return true;
                    } catch (Exception e) {
                        log.warn("[ES] standalone delete-by-field {}='{}' on '{}' failed: {}",
                                fieldName, fieldValue, indexName, e.getMessage());
                        return false;
                    }
                }).orElse(false);
    }

    @Override
    public boolean commitStandalone(TurSEInstance seInstance, String indexName) {
        // Elasticsearch refresh policy handles visibility automatically;
        // explicit commit-equivalent is _refresh — request it for the
        // index so RAG queries fired immediately after indexing see the
        // new docs (vs the 1-second default refresh window).
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    try {
                        instance.getClient().indices().refresh(r -> r.index(indexName));
                        return true;
                    } catch (Exception e) {
                        log.debug("[ES] standalone refresh '{}' failed (non-fatal): {}",
                                indexName, e.getMessage());
                        return false;
                    }
                }).orElse(true);
    }

    @Override
    public List<com.viglet.turing.plugins.se.TurSEStandaloneHit> retrieveStandalone(
            TurSEInstance seInstance, String indexName, String query, int topK) {
        if (query == null || query.isBlank() || topK < 1) {
            return List.of();
        }
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    try {
                        // match query on the canonical content field —
                        // ES applies the per-locale analyzer set up at
                        // provisioning. No need to escape: match parser
                        // doesn't interpret Lucene syntax. Map type token
                        // arrives via an unchecked cast because the ES SDK
                        // takes a runtime Class<T> and generic erasure
                        // makes Map<String, Object>.class unwritable.
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        Class<Map<String, Object>> mapType = (Class) Map.class;
                        var resp = instance.getClient().search(s -> s
                                .index(indexName)
                                .size(topK)
                                .source(src -> src
                                        .filter(f -> f.includes("id", CONTENT,
                                                ASSET_ID, CHUNK_INDEX, SOURCE_FILE)))
                                .query(q -> q
                                        .match(m -> m.field(CONTENT).query(query))),
                                mapType);
                        java.util.List<com.viglet.turing.plugins.se.TurSEStandaloneHit> hits =
                                new java.util.ArrayList<>(resp.hits().hits().size());
                        for (var hit : resp.hits().hits()) {
                            Map<String, Object> source = hit.source();
                            if (source == null) {
                                continue;
                            }
                            String id = hit.id();
                            String content = source.get(CONTENT) == null
                                    ? "" : source.get(CONTENT).toString();
                            // Extract once into a local so the IDE can prove
                            // the unbox below is null-safe.
                            Double rawScore = hit.score();
                            double score = rawScore == null ? 0.0 : rawScore.doubleValue();
                            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                            putIfPresent(metadata, ASSET_ID, source.get(ASSET_ID));
                            putIfPresent(metadata, CHUNK_INDEX, source.get(CHUNK_INDEX));
                            putIfPresent(metadata, SOURCE_FILE, source.get(SOURCE_FILE));
                            hits.add(new com.viglet.turing.plugins.se.TurSEStandaloneHit(
                                    id, content, metadata, score));
                        }
                        return hits;
                    } catch (Exception e) {
                        log.warn("[ES] standalone retrieve '{}' query='{}' failed: {}",
                                indexName, query, e.getMessage());
                        return java.util.List.<com.viglet.turing.plugins.se.TurSEStandaloneHit>of();
                    }
                }).orElse(List.of());
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * T28 / §III.5 — Elasticsearch {@code more_like_this} backend for
     * {@code TurSeMltIntentClassifier}. Submits the user transcript as
     * the {@code like} text on a per-agent intent index, scoring against
     * {@code field} (typically {@code "samples"}). Returns the top docs
     * whose ids are the catalog labels (catalogs are indexed one doc per
     * label).
     */
    @Override
    public List<com.viglet.turing.plugins.se.TurSEStandaloneHit> moreLikeThisStandalone(
            TurSEInstance seInstance, String indexName, String field, String text, int topK) {
        if (text == null || text.isBlank() || topK < 1) {
            return List.of();
        }
        return turElasticsearchInstanceProcess.initElasticsearchInstance(seInstance, indexName)
                .map(instance -> {
                    try {
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        Class<Map<String, Object>> mapType = (Class) Map.class;
                        var resp = instance.getClient().search(s -> s
                                .index(indexName)
                                .size(topK)
                                .source(src -> src.filter(f -> f.includes("id", LABEL)))
                                .query(q -> q.moreLikeThis(m -> m
                                        .fields(field)
                                        .like(l -> l.text(text))
                                        .minTermFreq(1)
                                        .minDocFreq(1)
                                        .minWordLength(3)
                                        .maxQueryTerms(40))),
                                mapType);
                        List<com.viglet.turing.plugins.se.TurSEStandaloneHit> hits =
                                new java.util.ArrayList<>(resp.hits().hits().size());
                        for (var hit : resp.hits().hits()) {
                            Map<String, Object> source = hit.source();
                            String label = source != null && source.get(LABEL) != null
                                    ? source.get(LABEL).toString()
                                    : hit.id();
                            Double rawScore = hit.score();
                            double score = rawScore == null ? 0.0 : rawScore.doubleValue();
                            hits.add(new com.viglet.turing.plugins.se.TurSEStandaloneHit(
                                    label, "", Map.of(), score));
                        }
                        return hits;
                    } catch (Exception e) {
                        log.warn("[ES] MLT standalone '{}' failed: {}", indexName, e.getMessage());
                        return java.util.List.<com.viglet.turing.plugins.se.TurSEStandaloneHit>of();
                    }
                }).orElse(List.of());
    }

    // ---- Similar documents (T384) ----------------------------------------

    private static final List<String> SIMILAR_FIELDS = List.of(
            TurSNFieldName.ID, TurSNFieldName.TITLE, TurSNFieldName.TYPE, TurSNFieldName.URL,
            TurSNFieldName.ABSTRACT, TurSNFieldName.TEXT);
    private static final int SIMILAR_MAX_SEED_CHARS = 1_000;

    @Override
    public List<TurSESimilarResult> getSimilarDocuments(TurSNSiteLocale siteLocale, String id, int rows) {
        if (id == null || id.isBlank() || rows < 1) {
            return List.of();
        }
        List<Map<String, Object>> seed = getDocumentsByIds(siteLocale, List.of(id));
        if (seed.isEmpty()) {
            return List.of();
        }
        String like = similarityText(seed.get(0));
        if (like.isBlank()) {
            return List.of();
        }
        return turElasticsearchInstanceProcess.initElasticsearchInstance(siteLocale)
                .map(instance -> searchSimilarDocuments(instance, like, id, rows))
                .orElse(List.of());
    }

    private List<TurSESimilarResult> searchSimilarDocuments(TurElasticsearchInstance instance,
            String like, String id, int rows) {
        try {
            var response = instance.getClient().search(s -> s
                    .index(instance.getIndex())
                    .size(rows + 1)
                    .source(src -> src.filter(f -> f.includes(TurSNFieldName.ID,
                            TurSNFieldName.TITLE, TurSNFieldName.TYPE, TurSNFieldName.URL)))
                    .query(q -> q.moreLikeThis(m -> m
                            .fields(TurSNFieldName.TITLE, TurSNFieldName.ABSTRACT,
                                    TurSNFieldName.TEXT)
                            .like(l -> l.text(like))
                            .minTermFreq(1)
                            .minDocFreq(1)
                            .minWordLength(3)
                            .maxQueryTerms(40))),
                    (java.lang.reflect.Type) Map.class);
            List<TurSESimilarResult> results = new java.util.ArrayList<>();
            for (var hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                String docId = firstString(source, TurSNFieldName.ID, hit.id());
                if (docId != null && !docId.equals(id)) {
                    results.add(TurSESimilarResult.builder()
                            .id(docId)
                            .title(firstString(source, TurSNFieldName.TITLE, null))
                            .type(firstString(source, TurSNFieldName.TYPE, null))
                            .url(firstString(source, TurSNFieldName.URL, null))
                            .build());
                    if (results.size() >= rows) {
                        break;
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[Elasticsearch] similar documents for id '{}' failed: {}",
                    id, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getDocumentsByIds(TurSNSiteLocale siteLocale, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> wanted = ids.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        return turElasticsearchInstanceProcess.initElasticsearchInstance(siteLocale)
                .map(instance -> collectDocumentsByIds(instance, wanted))
                .orElse(List.of());
    }

    /**
     * Runs the terms query for {@code wanted} ids and returns the matched
     * documents in request order (missing ids dropped). Per-call failures are
     * logged and yield an empty list. Extracted from the {@code .map(...)}
     * lambda in {@link #getDocumentsByIds} to keep cognitive complexity low.
     *
     * @since 2026.3.1
     */
    private List<Map<String, Object>> collectDocumentsByIds(TurElasticsearchInstance instance,
            List<String> wanted) {
        try {
            var response = instance.getClient().search(s -> s
                    .index(instance.getIndex())
                    .size(wanted.size())
                    .source(src -> src.filter(f -> f.includes(SIMILAR_FIELDS)))
                    .query(q -> q.terms(t -> t
                            .field(TurSNFieldName.ID)
                            .terms(tt -> tt.value(wanted.stream()
                                    .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                    .toList())))),
                    (java.lang.reflect.Type) Map.class);
            Map<String, Map<String, Object>> byId = new java.util.HashMap<>();
            for (var hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source == null) {
                    continue;
                }
                Map<String, Object> fields = extractSimilarFields(source);
                Object docId = fields.getOrDefault(TurSNFieldName.ID, hit.id());
                if (docId != null) {
                    byId.put(docId.toString(), fields);
                }
            }
            List<Map<String, Object>> ordered = new java.util.ArrayList<>();
            for (String key : wanted) {
                Map<String, Object> fields = byId.get(key);
                if (fields != null) {
                    ordered.add(fields);
                }
            }
            return ordered;
        } catch (Exception e) {
            log.warn("[Elasticsearch] getDocumentsByIds failed: {}", e.getMessage());
            return List.<Map<String, Object>>of();
        }
    }

    /**
     * Projects a raw ES {@code _source} map down to {@link #SIMILAR_FIELDS},
     * unwrapping single-element collections to their scalar and dropping null
     * values.
     *
     * @since 2026.3.1
     */
    private static Map<String, Object> extractSimilarFields(Map<String, Object> source) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : SIMILAR_FIELDS) {
            Object value = source.get(field);
            if (value instanceof java.util.Collection<?> col) {
                value = col.isEmpty() ? null : col.iterator().next();
            }
            if (value != null) {
                fields.put(field, value);
            }
        }
        return fields;
    }

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

    private static String firstString(Map<String, Object> source, String field, String fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(field);
        if (value instanceof java.util.Collection<?> col) {
            value = col.isEmpty() ? null : col.iterator().next();
        }
        return value == null ? fallback : value.toString();
    }

    // ---- Content export ---------------------------------------------------

    @Override
    public List<Map<String, Object>> retrieveDocumentPage(TurSNSiteLocale turSNSiteLocale,
            Map<String, ?> siteFieldMap, int start, int rows) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(turSNSiteLocale)
                .map(instance -> {
                    try {
                        var response = instance.getClient().search(s -> s
                                .index(instance.getIndex())
                                .query(q -> q.matchAll(m -> m))
                                .from(start)
                                .size(rows)
                                .sort(so -> so.field(f -> f.field("id").order(
                                        co.elastic.clients.elasticsearch._types.SortOrder.Asc))),
                                (java.lang.reflect.Type) Map.class);
                        List<Map<String, Object>> docs = new java.util.ArrayList<>();
                        for (var hit : response.hits().hits()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> source = (Map<String, Object>) hit.source();
                            if (source == null) continue;
                            Map<String, Object> fields = new java.util.LinkedHashMap<>();
                            for (var entry : source.entrySet()) {
                                if (siteFieldMap.containsKey(entry.getKey())
                                        || "id".equals(entry.getKey())
                                        || "type".equals(entry.getKey())) {
                                    fields.put(entry.getKey(), entry.getValue());
                                }
                            }
                            docs.add(fields);
                        }
                        return docs;
                    } catch (Exception e) {
                        log.warn("[Elasticsearch] Failed to retrieve document page: {}", e.getMessage());
                        return List.<Map<String, Object>>of();
                    }
                }).orElse(List.of());
    }

    // ---- Monitoring ------------------------------------------------------

    @Override
    public long getDocumentTotal(TurSNSiteLocale turSNSiteLocale) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(turSNSiteLocale)
                .map(turElasticsearch::getDocumentTotal).orElse(0L);
    }

    @Override
    public long getDocumentCountWithField(TurSNSiteLocale turSNSiteLocale, String fieldName) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(turSNSiteLocale)
                .map(instance -> turElasticsearch.getDocumentTotalWithField(instance, fieldName))
                .orElse(-1L);
    }

    @Override
    public long getDocumentCountWithFieldBelow(TurSNSiteLocale turSNSiteLocale, String fieldName,
            int threshold) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(turSNSiteLocale)
                .map(instance -> turElasticsearch.getDocumentTotalWithFieldBelow(instance, fieldName,
                        threshold))
                .orElse(-1L);
    }

    // ---- System info -----------------------------------------------------

    @Override
    public Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        var info = new LinkedHashMap<String, String>();
        info.put("engine", "Elasticsearch");
        try {
            String baseUrl = seInstance.getEndpointUrl();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl))
                    .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response.body());
            var version = root.path("version");
            if (!version.isMissingNode()) {
                info.put("version", version.path("number").asText(""));
                info.put("luceneVersion", version.path("lucene_version").asText(""));
                info.put("buildType", version.path("build_type").asText(""));
            }
            info.put("clusterName", root.path("cluster_name").asText(""));
            info.put(STATUS, "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put(STATUS, "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put(STATUS, "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }
}
