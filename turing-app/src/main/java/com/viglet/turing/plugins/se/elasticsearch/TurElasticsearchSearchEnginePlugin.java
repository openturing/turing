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
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.elasticsearch.TurElasticsearchUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                    if (queued == 0) {
                        return 0;
                    }
                    try {
                        co.elastic.clients.elasticsearch.core.BulkResponse resp =
                                instance.getClient().bulk(bulk.build());
                        // The bulk API may partially succeed — only count
                        // items without an errors flag. resp.errors() is
                        // the global flag; per-item check is more precise.
                        if (resp.errors()) {
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
                        return queued;
                    } catch (Exception e) {
                        log.warn("[ES] bulk standalone '{}' failed: {}", indexName, e.getMessage());
                        return 0;
                    }
                }).orElse(0);
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
                                        .filter(f -> f.includes("id", "content",
                                                "assetId", "chunkIndex", "sourceFile")))
                                .query(q -> q
                                        .match(m -> m.field("content").query(query))),
                                mapType);
                        java.util.List<com.viglet.turing.plugins.se.TurSEStandaloneHit> hits =
                                new java.util.ArrayList<>(resp.hits().hits().size());
                        for (var hit : resp.hits().hits()) {
                            Map<String, Object> source = hit.source();
                            if (source == null) {
                                continue;
                            }
                            String id = hit.id();
                            String content = source.get("content") == null
                                    ? "" : source.get("content").toString();
                            // Extract once into a local so the IDE can prove
                            // the unbox below is null-safe.
                            Double rawScore = hit.score();
                            double score = rawScore == null ? 0.0 : rawScore.doubleValue();
                            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                            putIfPresent(metadata, "assetId", source.get("assetId"));
                            putIfPresent(metadata, "chunkIndex", source.get("chunkIndex"));
                            putIfPresent(metadata, "sourceFile", source.get("sourceFile"));
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
                                .source(src -> src.filter(f -> f.includes("id", "label")))
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
                            String label = source != null && source.get("label") != null
                                    ? source.get("label").toString()
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
            info.put("status", "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve Elasticsearch system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }
}
