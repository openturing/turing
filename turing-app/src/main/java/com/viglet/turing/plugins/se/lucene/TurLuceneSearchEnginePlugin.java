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
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.lucene.TurLuceneQueryBuilder;
import com.viglet.turing.lucene.TurLuceneUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSESynonymApplyResult;
import com.viglet.turing.plugins.se.TurSESynonymRule;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final com.viglet.turing.lucene.TurLuceneStorageSync storageSync;
    private final TurLuceneSynonymRegistry synonymRegistry;

    public TurLuceneSearchEnginePlugin(TurLucene turLucene,
            TurLuceneInstanceProcess turLuceneInstanceProcess,
            TurLuceneQueryBuilder turLuceneQueryBuilder,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            com.viglet.turing.lucene.TurLuceneStorageSync storageSync,
            TurLuceneSynonymRegistry synonymRegistry) {
        this.turLucene = turLucene;
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
        this.turLuceneQueryBuilder = turLuceneQueryBuilder;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.storageSync = storageSync;
        this.synonymRegistry = synonymRegistry;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turLuceneInstanceProcess
                .initLuceneInstance(context.getSiteName(), context.getLocale())
                .flatMap(instance -> turLucene.retrieveLuceneFromSN(instance, context)
                        .map(results -> withSpellCheck(results, instance, context))
                        .map(results -> withMoreLikeThis(results, instance, context)));
    }

    /**
     * Populates the query-level "Related" (MoreLikeThis) block on the search
     * results, mirroring the Solr main-search path
     * ({@code TurSolrResultProcessor#setMLT}). Without this the Lucene engine
     * always returned an empty {@code similarResults}, so the search-widget
     * "Related" panel never rendered for Lucene-backed sites (e.g. WKND on
     * turing-demo) while it worked on Solr-backed sites.
     *
     * <p>Gated exactly like Solr: the site must have MoreLikeThis enabled
     * ({@code site.getMlt()}) and at least one enabled MLT field configured.
     * For each result document (capped) it seeds a Lucene {@link MoreLikeThis}
     * query and merges the hits, de-duplicated and excluding documents already
     * present in the result set. Reuses the {@link TurLuceneInstance} already
     * open for the search. Fail-open: any error leaves the results untouched.
     */
    private TurSEResults withMoreLikeThis(TurSEResults results, TurLuceneInstance instance,
            TurSNSiteSearchContext context) {
        if (results.getSimilarResults() != null && !results.getSimilarResults().isEmpty()) {
            return results;
        }
        List<TurSEResult> docs = results.getResults();
        if (docs == null || docs.isEmpty()) {
            return results;
        }
        try {
            TurSNSite site = turSNSiteRepository.findByName(context.getSiteName()).orElse(null);
            String[] mltFields = resolveMltFields(site);
            if (site == null || !TurSNUtils.isTrue(site.getMlt())
                    || turSNSiteFieldExtRepository.findByTurSNSiteAndMltAndEnabled(site, 1, 1).isEmpty()) {
                return results;
            }
            results.setSimilarResults(collectMoreLikeThis(instance, docs, mltFields));
        } catch (Exception e) {
            log.warn("[Lucene] MoreLikeThis enrichment for site '{}' failed: {}",
                    context.getSiteName(), e.getMessage());
        }
        return results;
    }

    /**
     * Runs {@link MoreLikeThis} seeded from each result document (up to
     * {@link #MLT_MAX_SEED_DOCS}) and merges the hits into a single ordered,
     * de-duplicated list, dropping any document already in the result set.
     */
    private List<TurSESimilarResult> collectMoreLikeThis(TurLuceneInstance instance,
            List<TurSEResult> docs, String[] mltFields) {
        List<String> seedIds = docs.stream()
                .map(doc -> asString(doc.getFields().get(TurSNFieldName.ID)))
                .filter(id -> id != null && !id.isBlank())
                .limit(MLT_MAX_SEED_DOCS)
                .toList();
        if (seedIds.isEmpty()) {
            return List.of();
        }
        Set<String> resultIds = docs.stream()
                .map(doc -> asString(doc.getFields().get(TurSNFieldName.ID)))
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, TurSESimilarResult> merged = new LinkedHashMap<>();
        for (String seedId : seedIds) {
            String like = seedText(instance, seedId, mltFields);
            if (like.isBlank()) {
                continue;
            }
            for (TurSESimilarResult hit : searchSimilarDocuments(instance, like, seedId,
                    MLT_ROWS_PER_SEED, mltFields)) {
                if (hit.getId() != null && !resultIds.contains(hit.getId())) {
                    merged.putIfAbsent(hit.getId(), hit);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * T686 — populates the "did you mean" suggestion on the Lucene search
     * results when the engine didn't already set one, mirroring the Solr main
     * search path (which spell-checks every query in {@code retrieveSolrFromSN}).
     * Reuses the same {@link TurLuceneInstance} already opened for the search so
     * no second core lookup is needed. Fail-open: any error leaves the results
     * untouched.
     */
    private TurSEResults withSpellCheck(TurSEResults results, TurLuceneInstance instance,
            TurSNSiteSearchContext context) {
        if (results.getSpellCheck() != null || context.getTurSEParameters() == null) {
            return results;
        }
        String query = context.getTurSEParameters().getQuery();
        if (query != null && !query.isBlank()) {
            results.setSpellCheck(computeSpellCheck(instance, query));
        }
        return results;
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

    // ---- Synonyms (T665 / §XXXIX, Block AP) ------------------------------

    @Override
    public boolean supportsSynonyms() {
        return true;
    }

    @Override
    public TurSESynonymApplyResult applySynonyms(TurSEInstance seInstance, String indexName,
            Locale locale, List<TurSESynonymRule> rules) {
        // Embedded, single-JVM: "apply" = swap the query-time SynonymMap the query
        // analyzer consults for this core. No reindex — the next query expands.
        TurLuceneSynonymMap built = TurLuceneSynonymMapBuilder.build(rules);
        synonymRegistry.put(indexName, built.map());
        return new TurSESynonymApplyResult(true, built.appliedRules(),
                built.unsupportedTypes(), built.warnings());
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

    /**
     * T804 / §LV.2 — real bulk override for the embedded Lucene engine: pushes the
     * whole batch through one {@code IndexWriter} session with a single trailing
     * {@code commit()} (see {@link com.viglet.turing.lucene.TurLuceneDocumentHandler#indexingBatch}),
     * so a catalog-scale import pays one flush rather than one per document. Any
     * failure throws so {@code TurSNProcessQueue} degrades to the per-document
     * path, which carries the schema-conflict / stale-writer self-healing retries.
     *
     * @return the number of documents written to the index
     * @since 2026.3.4
     */
    @Override
    public int indexDocuments(TurSNSite turSNSite, Locale locale,
            List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), locale)
                .map(luceneInstance -> turLucene.indexingBatch(luceneInstance, turSNSite, documents))
                .orElse(0);
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
                                org.apache.lucene.search.MatchAllDocsQuery.INSTANCE,
                                start + rows);
                        var scoreDocs = topDocs.scoreDocs;
                        log.info("[Lucene] Search returned {} scoreDocs (requested start={} rows={})",
                                scoreDocs.length, start, rows);
                        List<Map<String, Object>> docs = new java.util.ArrayList<>();
                        for (int i = start; i < scoreDocs.length && i < start + rows; i++) {
                            var doc = searcher.storedFields().document(scoreDocs[i].doc);
                            docs.add(extractDocFields(doc, siteFieldMap));
                        }
                        return docs;
                    } catch (Exception e) {
                        log.warn("[Lucene] Failed to retrieve document page: {}", e.getMessage());
                        return List.<Map<String, Object>>of();
                    }
                }).orElse(List.of());
    }

    /** Projects a stored doc into the requested site fields (plus id/type), folding multi-values into lists. */
    private Map<String, Object> extractDocFields(org.apache.lucene.document.Document doc,
            Map<String, ?> siteFieldMap) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        for (var field : doc.getFields()) {
            String name = field.name();
            if (!siteFieldMap.containsKey(name) && !"id".equals(name) && !"type".equals(name)) {
                continue;
            }
            mergeDocField(fields, name, field.stringValue());
        }
        return fields;
    }

    private void mergeDocField(Map<String, Object> fields, String name, String value) {
        Object existing = fields.get(name);
        if (existing == null) {
            fields.put(name, value);
        } else if (existing instanceof List<?> list) {
            // Multi-valued: collect into the existing list.
            @SuppressWarnings("unchecked")
            var mutableList = (List<Object>) list;
            mutableList.add(value);
        } else {
            var newList = new java.util.ArrayList<>();
            newList.add(existing);
            newList.add(value);
            fields.put(name, newList);
        }
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
                collectPrefixMatches(doc, prefix, leadingContext, seen);
                if (seen.size() >= maxHits * 2) break;
            }

            return seen.stream().limit(maxHits).toList();
        } catch (Exception e) {
            log.warn("[Lucene] Autocomplete error: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Adds every stored-field word that starts with {@code prefix} (case
     * insensitive) to {@code seen}, prepended with the already-typed
     * {@code leadingContext}.
     *
     * <p>Only <em>edge</em> punctuation is trimmed (a trailing comma/period,
     * surrounding quotes/parentheses); punctuation <em>inside</em> a token — the
     * hyphen in {@code self-hosting}, the apostrophe in {@code user's} — is kept.
     * Stripping internal hyphens produced suggestions like {@code selfhosting}
     * that, when clicked, matched nothing: the index is tokenized with
     * {@code StandardAnalyzer}, which splits {@code self-hosting} into
     * {@code self}+{@code hosting}, so only the verbatim hyphenated term
     * round-trips through the main query analyzer back to real results.
     */
    private void collectPrefixMatches(org.apache.lucene.document.Document doc, String prefix,
            String leadingContext, Set<String> seen) {
        for (var field : doc.getFields()) {
            String value = field.stringValue();
            if (value == null) continue;
            // Tokenize stored value and find words matching the prefix
            for (String word : value.split("\\s+")) {
                String clean = normalizeSuggestionToken(word);
                if (clean.length() >= prefix.length() && clean.startsWith(prefix)) {
                    seen.add(leadingContext + clean);
                }
            }
        }
    }

    /**
     * Lower-cases a stored-value token and trims only <em>edge</em> punctuation,
     * preserving punctuation inside the token (the hyphen in {@code self-hosting},
     * the apostrophe in {@code user's}). Package-private for direct unit testing
     * of the hyphen round-trip fix.
     */
    static String normalizeSuggestionToken(String word) {
        return word.toLowerCase()
                .replaceAll("^[^\\p{L}\\p{N}]+", "")   // trim leading punctuation
                .replaceAll("[^\\p{L}\\p{N}]+$", "");  // trim trailing punctuation
    }

    // ---- Similar documents (T685 / §XLI.1, Block AR) --------------------

    /** Standard fields fetched for similar-document hydration / seed content. */
    private static final List<String> SIMILAR_FIELDS = List.of(
            TurSNFieldName.ID, TurSNFieldName.TITLE, TurSNFieldName.TYPE, TurSNFieldName.URL,
            TurSNFieldName.ABSTRACT, TurSNFieldName.TEXT);
    /** Text fields {@link MoreLikeThis} scores against. */
    private static final String[] SIMILAR_MLT_FIELDS = {
            TurSNFieldName.TITLE, TurSNFieldName.ABSTRACT, TurSNFieldName.TEXT};
    /** Cap the seed text fed to MLT so a huge document can't blow up the query. */
    private static final int SIMILAR_MAX_SEED_CHARS = 1_000;
    /** Max result documents used to seed the query-level "Related" MoreLikeThis. */
    private static final int MLT_MAX_SEED_DOCS = 10;
    /** Similar hits requested per seed document before merge/de-dup. */
    private static final int MLT_ROWS_PER_SEED = 5;

    @Override
    public List<Map<String, Object>> getDocumentsByIds(TurSNSiteLocale siteLocale, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> wanted = ids.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        return turLuceneInstanceProcess.initLuceneInstance(siteLocale)
                .map(instance -> fetchByIds(instance, wanted))
                .orElse(List.of());
    }

    /** Fetches the standard fields for {@code ids} via a term lookup on {@code id}, preserving input order. */
    private List<Map<String, Object>> fetchByIds(TurLuceneInstance instance, List<String> ids) {
        try {
            IndexSearcher searcher = instance.getSearcher();
            Map<String, Map<String, Object>> byId = new HashMap<>();
            for (String id : ids) {
                TopDocs topDocs = searcher.search(
                        new TermQuery(new Term(TurSNFieldName.ID, id)), 1);
                if (topDocs.scoreDocs.length > 0) {
                    Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
                    byId.put(id, extractSimilarFields(doc));
                }
            }
            List<Map<String, Object>> ordered = new ArrayList<>();
            for (String id : ids) {
                Map<String, Object> fields = byId.get(id);
                if (fields != null) {
                    ordered.add(fields);
                }
            }
            return ordered;
        } catch (Exception e) {
            log.warn("[Lucene] getDocumentsByIds failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Projects a stored doc down to {@link #SIMILAR_FIELDS} (first stored value), dropping absent fields. */
    private static Map<String, Object> extractSimilarFields(Document doc) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : SIMILAR_FIELDS) {
            String value = doc.get(field);
            if (value != null) {
                fields.put(field, value);
            }
        }
        return fields;
    }

    @Override
    public List<TurSESimilarResult> getSimilarDocuments(TurSNSiteLocale siteLocale, String id, int rows) {
        if (id == null || id.isBlank() || rows < 1) {
            return List.of();
        }
        String[] mltFields = resolveMltFields(siteLocale == null ? null : siteLocale.getTurSNSite());
        return turLuceneInstanceProcess.initLuceneInstance(siteLocale)
                .map(instance -> {
                    String like = seedText(instance, id, mltFields);
                    return like.isBlank()
                            ? List.<TurSESimilarResult>of()
                            : searchSimilarDocuments(instance, like, id, rows, mltFields);
                })
                .orElse(List.of());
    }

    /** Runs a Lucene {@link MoreLikeThis} query built from the seed text, excluding the seed and capping at {@code rows}. */
    private List<TurSESimilarResult> searchSimilarDocuments(TurLuceneInstance instance, String like,
            String id, int rows, String[] mltFields) {
        try {
            IndexReader reader = instance.getReader();
            IndexSearcher searcher = instance.getSearcher();
            MoreLikeThis mlt = new MoreLikeThis(reader);
            mlt.setAnalyzer(new StandardAnalyzer());
            mlt.setFieldNames(mltFields);
            mlt.setMinTermFreq(1);
            mlt.setMinDocFreq(1);
            mlt.setMinWordLen(3);
            mlt.setMaxQueryTerms(40);
            // Seed the interesting-term extraction from the first configured MLT
            // field; setFieldNames above is what actually scans all MLT fields.
            Query query = mlt.like(mltFields[0], new StringReader(like));
            TopDocs topDocs = searcher.search(query, rows + 1);
            List<TurSESimilarResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String docId = doc.get(TurSNFieldName.ID);
                if (docId == null || docId.equals(id)) {
                    continue;
                }
                results.add(TurSESimilarResult.builder()
                        .id(docId)
                        .title(doc.get(TurSNFieldName.TITLE))
                        .type(doc.get(TurSNFieldName.TYPE))
                        .url(doc.get(TurSNFieldName.URL))
                        .build());
                if (results.size() >= rows) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[Lucene] getSimilarDocuments for id '{}' failed: {}", id, e.getMessage());
            return List.of();
        }
    }

    /**
     * Resolves the MoreLikeThis field set for a site from its per-field
     * {@code mlt} attribute (the fields where {@code mlt==1 && enabled==1}),
     * mirroring Solr's {@code prepareQueryMLT}. Falls back to the legacy
     * {@link #SIMILAR_MLT_FIELDS} when the site is unknown or has no MLT field
     * configured, so behaviour is unchanged for sites that never set the flag.
     */
    private String[] resolveMltFields(TurSNSite site) {
        if (site != null) {
            List<String> configured = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndMltAndEnabled(site, 1, 1).stream()
                    .map(TurSNSiteFieldExt::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            if (!configured.isEmpty()) {
                return configured.toArray(new String[0]);
            }
        }
        return SIMILAR_MLT_FIELDS;
    }

    /**
     * Builds the MoreLikeThis seed text from the seed document's configured MLT
     * fields (ALL stored values, so a multi-valued body field like {@code
     * paragraphs} contributes fully), capped to {@link #SIMILAR_MAX_SEED_CHARS}.
     * Returns an empty string when the seed id is not found or has no text in
     * those fields.
     */
    private String seedText(TurLuceneInstance instance, String id, String[] mltFields) {
        try {
            IndexSearcher searcher = instance.getSearcher();
            TopDocs topDocs = searcher.search(new TermQuery(new Term(TurSNFieldName.ID, id)), 1);
            if (topDocs.scoreDocs.length == 0) {
                return "";
            }
            Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            StringBuilder sb = new StringBuilder();
            for (String field : mltFields) {
                for (String value : doc.getValues(field)) {
                    if (value != null && !value.isBlank()) {
                        if (!sb.isEmpty()) {
                            sb.append(' ');
                        }
                        sb.append(value.trim());
                    }
                }
            }
            String text = sb.toString().trim();
            return text.length() > SIMILAR_MAX_SEED_CHARS
                    ? text.substring(0, SIMILAR_MAX_SEED_CHARS)
                    : text;
        } catch (Exception e) {
            log.warn("[Lucene] seedText for id '{}' failed: {}", id, e.getMessage());
            return "";
        }
    }

    // ---- Spell check (T686 / §XLI.2, Block AR) --------------------------

    /** Candidate text fields the spell-checker consults, in priority order. */
    private static final String[] SPELL_CHECK_FIELDS = {
            TurSNFieldName.TEXT, TurSNFieldName.TITLE, TurSNFieldName.ABSTRACT};

    @Override
    public TurSESpellCheckResult spellCheck(String siteName, String term, Locale locale) {
        if (term == null || term.isBlank()) {
            return new TurSESpellCheckResult();
        }
        return turLuceneInstanceProcess.initLuceneInstance(siteName, locale)
                .map(instance -> computeSpellCheck(instance, term))
                .orElseGet(TurSESpellCheckResult::new);
    }

    /**
     * Token-by-token "did you mean" over the live index using
     * {@link DirectSpellChecker}. A token already present in any candidate field
     * is kept verbatim; an unindexed token is replaced by its best suggestion.
     * The result is corrected only when at least one token changed. Fail-open:
     * any error yields an uncorrected result.
     */
    private TurSESpellCheckResult computeSpellCheck(TurLuceneInstance instance, String term) {
        try {
            IndexReader reader = instance.getReader();
            DirectSpellChecker checker = new DirectSpellChecker();
            String[] tokens = term.trim().toLowerCase().split("\\s+");
            List<String> out = new ArrayList<>(tokens.length);
            boolean corrected = false;
            for (String token : tokens) {
                if (token.isBlank()) {
                    continue;
                }
                String suggestion = bestSuggestion(checker, reader, token);
                if (suggestion != null) {
                    out.add(suggestion);
                    corrected = true;
                } else {
                    out.add(token);
                }
            }
            if (!corrected) {
                return new TurSESpellCheckResult();
            }
            return new TurSESpellCheckResult(true, String.join(" ", out));
        } catch (Exception e) {
            log.warn("[Lucene] spell-check for '{}' failed: {}", term, e.getMessage());
            return new TurSESpellCheckResult();
        }
    }

    /**
     * Returns the best correction for {@code token}, or {@code null} when the
     * token is already indexed in some candidate field (treated as correctly
     * spelled) or no suggestion clears the checker's threshold.
     */
    private static String bestSuggestion(DirectSpellChecker checker, IndexReader reader, String token)
            throws IOException {
        for (String field : SPELL_CHECK_FIELDS) {
            if (reader.docFreq(new Term(field, token)) > 0) {
                return null; // present in the index — accept the token as spelled
            }
        }
        for (String field : SPELL_CHECK_FIELDS) {
            SuggestWord[] words = checker.suggestSimilar(new Term(field, token), 1, reader,
                    SuggestMode.SUGGEST_ALWAYS);
            if (words.length > 0 && !words[0].string.equals(token)) {
                return words[0].string;
            }
        }
        return null;
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

    @Override
    public long getDocumentCountWithField(TurSNSiteLocale turSNSiteLocale, String fieldName) {
        return turLuceneInstanceProcess.initLuceneInstance(turSNSiteLocale)
                .map(instance -> {
                    try {
                        // FieldExistsQuery matches documents that have any value
                        // indexed for the field (points, doc values, norms or
                        // terms) — the Lucene analogue of Solr's field:[* TO *].
                        return (long) instance.getSearcher()
                                .count(new org.apache.lucene.search.FieldExistsQuery(fieldName));
                    } catch (Exception e) {
                        log.warn("[Lucene] Could not count documents with field '{}': {}",
                                fieldName, e.getMessage());
                        return -1L;
                    }
                }).orElse(-1L);
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
