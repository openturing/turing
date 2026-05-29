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
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.lucene.TurLuceneQueryBuilder;
import com.viglet.turing.lucene.TurLuceneUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
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
import java.util.Set;

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
    private final TurLuceneQueryBuilder turLuceneQueryBuilder;
    private final TurSNSiteRepository turSNSiteRepository;
    private final com.viglet.turing.lucene.TurLuceneStorageSync storageSync;

    public TurLuceneSearchEnginePlugin(TurLucene turLucene,
            TurLuceneInstanceProcess turLuceneInstanceProcess,
            TurLuceneQueryBuilder turLuceneQueryBuilder,
            TurSNSiteRepository turSNSiteRepository,
            com.viglet.turing.lucene.TurLuceneStorageSync storageSync) {
        this.turLucene = turLucene;
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
        this.turLuceneQueryBuilder = turLuceneQueryBuilder;
        this.turSNSiteRepository = turSNSiteRepository;
        this.storageSync = storageSync;
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
        // Close and drop any cached IndexWriter first — otherwise the directory is
        // deleted underneath an open writer, leaving a stale instance whose write.lock
        // is gone (NoSuchFileException -> AlreadyClosedException on the next index call).
        turLuceneInstanceProcess.evict(seInstance.getEndpointUrl(), indexName);
        TurLuceneUtils.deleteCore(seInstance.getEndpointUrl(), indexName);
        if (storageSync != null && storageSync.isEnabled()) {
            storageSync.deleteFromStorage(indexName);
        }
    }

    @Override
    public void clearIndex(TurSEInstance seInstance, String indexName) {
        // Lucene: clear by deleting and recreating the index. Evict the cached writer
        // first so the recreated core is reopened fresh instead of reusing the orphaned
        // writer (see deleteIndex above).
        turLuceneInstanceProcess.evict(seInstance.getEndpointUrl(), indexName);
        TurLuceneUtils.deleteCore(seInstance.getEndpointUrl(), indexName);
        TurLuceneUtils.createCore(seInstance.getEndpointUrl(), indexName);
        if (storageSync != null && storageSync.isEnabled()) {
            storageSync.deleteFromStorage(indexName);
        }
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
                        if (e.getMessage() != null && isSchemaConflict(e.getMessage())) {
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
                    } catch (org.apache.lucene.store.AlreadyClosedException e) {
                        // The cached IndexWriter was closed underneath us — typically the core
                        // directory was deleted/recreated (clear/reindex) without evicting the
                        // cache, so write.lock is missing and the writer tragically closed.
                        // Rebuild the instance (preserving any existing index files) and retry
                        // once, so a single stale writer can't keep tripping the circuit breaker.
                        log.warn("[Lucene] IndexWriter closed (stale instance) for core '{}'. "
                                + "Rebuilding and retrying. Cause: {}",
                                luceneInstance.getIndexPath().getFileName(), e.getMessage());
                        return turLuceneInstanceProcess.rebuildInstance(luceneInstance.getIndexPath())
                                .map(fresh -> {
                                    turLucene.indexing(fresh, turSNSite, attributes);
                                    return true;
                                }).orElse(false);
                    }
                }).orElse(false);
    }

    private static boolean isSchemaConflict(String message) {
        return message.contains("doc values type")
                || message.contains("index options")
                || message.contains("cannot change field");
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

    // ---- Content export ---------------------------------------------------

    @Override
    public List<Map<String, Object>> retrieveDocumentPage(TurSNSiteLocale turSNSiteLocale,
            Map<String, ?> siteFieldMap, int start, int rows) {
        log.info("[Lucene] retrieveDocumentPage locale={} start={} rows={} siteFields={}",
                turSNSiteLocale.getLanguage(), start, rows, siteFieldMap.size());
        return turLuceneInstanceProcess.initLuceneInstance(turSNSiteLocale)
                .map(instance -> {
                    try {
                        var searcher = instance.getSearcher();
                        int totalDocs = searcher.getIndexReader().numDocs();
                        log.info("[Lucene] IndexReader numDocs={}", totalDocs);
                        if (totalDocs == 0) {
                            return List.<Map<String, Object>>of();
                        }
                        var topDocs = searcher.search(
                                new org.apache.lucene.search.MatchAllDocsQuery(),
                                start + rows);
                        var scoreDocs = topDocs.scoreDocs;
                        log.info("[Lucene] Search returned {} scoreDocs (requested start={} rows={})",
                                scoreDocs.length, start, rows);
                        List<Map<String, Object>> docs = new java.util.ArrayList<>();
                        for (int i = start; i < scoreDocs.length && i < start + rows; i++) {
                            var doc = searcher.storedFields().document(scoreDocs[i].doc);
                            Map<String, Object> fields = new java.util.LinkedHashMap<>();
                            for (var field : doc.getFields()) {
                                String name = field.name();
                                if (!siteFieldMap.containsKey(name) && !"id".equals(name) && !"type".equals(name)) {
                                    continue;
                                }
                                Object existing = fields.get(name);
                                if (existing != null) {
                                    // Multi-valued: collect into list
                                    if (existing instanceof List<?> list) {
                                        @SuppressWarnings("unchecked")
                                        var mutableList = (List<Object>) list;
                                        mutableList.add(field.stringValue());
                                    } else {
                                        var list = new java.util.ArrayList<>();
                                        list.add(existing);
                                        list.add(field.stringValue());
                                        fields.put(name, list);
                                    }
                                } else {
                                    fields.put(name, field.stringValue());
                                }
                            }
                            docs.add(fields);
                        }
                        return docs;
                    } catch (Exception e) {
                        log.warn("[Lucene] Failed to retrieve document page: {}", e.getMessage());
                        return List.<Map<String, Object>>of();
                    }
                }).orElse(List.of());
    }

    // ---- Autocomplete -----------------------------------------------------

    @Override
    public List<String> autoComplete(String siteName, String term, Locale locale, long rows) {
        if (term == null || term.length() < 2) {
            return List.of();
        }
        var optInstance = turLuceneInstanceProcess.initLuceneInstance(siteName, locale);
        var optSite = turSNSiteRepository.findByNameIgnoreCase(siteName);
        if (optInstance.isEmpty() || optSite.isEmpty()) {
            return List.of();
        }
        return executeAutoComplete(optInstance.get(), optSite.get(), term, rows);
    }

    /**
     * Extracts individual word-level suggestions from the Lucene index,
     * mimicking Solr's suggest component behavior.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Split query into tokens; the last token is the prefix being typed.</li>
     *   <li>Search ngram {@code _ac} fields to find matching documents.</li>
     *   <li>From matched docs, extract all stored text fields and tokenize them.</li>
     *   <li>Collect words that start with the prefix and rebuild multi-word phrases
     *       preserving the leading context words.</li>
     * </ol>
     */
    private List<String> executeAutoComplete(TurLuceneInstance instance,
            TurSNSite turSNSite, String term, long rows) {
        try {
            var searcher = instance.getSearcher();
            int maxHits = rows > 0 ? (int) rows : 20;

            var query = turLuceneQueryBuilder.buildAutoCompleteQuery(turSNSite, term);
            var topDocs = searcher.search(query, maxHits * 5);

            // Split query: leading completed words + last partial token
            String[] queryTokens = term.trim().toLowerCase().split("\\s+");
            String prefix = queryTokens[queryTokens.length - 1];
            String leadingContext = queryTokens.length > 1
                    ? String.join(" ", java.util.Arrays.copyOf(queryTokens, queryTokens.length - 1)) + " "
                    : "";

            Set<String> seen = new java.util.LinkedHashSet<>();
            for (var scoreDoc : topDocs.scoreDocs) {
                var doc = searcher.storedFields().document(scoreDoc.doc);
                for (var field : doc.getFields()) {
                    String value = field.stringValue();
                    if (value == null) continue;
                    // Tokenize stored value and find words matching the prefix
                    for (String word : value.split("\\s+")) {
                        String clean = word.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
                        if (clean.length() >= prefix.length() && clean.startsWith(prefix)) {
                            seen.add(leadingContext + clean);
                        }
                    }
                }
                if (seen.size() >= maxHits * 2) break;
            }

            return seen.stream().limit(maxHits).toList();
        } catch (Exception e) {
            log.warn("[Lucene] Autocomplete error: {}", e.getMessage());
            return List.of();
        }
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
