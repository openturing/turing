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
package com.viglet.turing.plugins.se;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.solr.bean.TurSECoreInfo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for search engine plugins that can be used by Turing.
 * Implementations include Solr, Elasticsearch, and Lucene.
 *
 * <p>Each plugin owns all index lifecycle, schema, document, and monitoring
 * operations for its engine. Callers should resolve the plugin via
 * {@link TurSearchEnginePluginFactory} and call the appropriate method without
 * needing to know which engine is in use.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
public interface TurSearchEnginePlugin {

    // ---- Search (existing) -----------------------------------------------

    Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context);

    Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName);

    String getPluginType();

    // ---- Index lifecycle -------------------------------------------------

    /**
     * Creates an index/core for the given SE instance.
     * Solr uses the locale language to pick the configSet; Lucene/ES ignore siteLocale.
     */
    void createIndex(TurSEInstance seInstance, TurSNSiteLocale siteLocale, String indexName,
            Map<String, TurSEFieldType> fieldTypes);

    /**
     * Creates a standalone index/core without requiring a site locale entity.
     * Uses only the language to determine the configSet (Solr) or
     * creates the index directly (Lucene/ES).
     *
     * @param seInstance the search engine instance
     * @param language   the locale language (e.g., Locale.forLanguageTag("en"))
     * @param indexName  the name for the new index/core
     */
    default void createStandaloneIndex(TurSEInstance seInstance, Locale language, String indexName) {
        TurSNSiteLocale tempLocale = new TurSNSiteLocale();
        tempLocale.setLanguage(language);
        createIndex(seInstance, tempLocale, indexName, Map.of());
    }

    void deleteIndex(TurSEInstance seInstance, String indexName);

    void clearIndex(TurSEInstance seInstance, String indexName);

    boolean indexExists(TurSEInstance seInstance, String indexName);

    List<TurSECoreInfo> listIndexes(TurSEInstance seInstance);

    // ---- Schema management (Lucene/ES are schema-less -> no-op/false) ----

    /**
     * Adds or updates a field in the index schema.
     * @param isNew true = add-field, false = replace-field (Solr only; ignored by others)
     */
    void addOrUpdateField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean stored, boolean multiValued, boolean isNew);

    void deleteField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType);

    boolean fieldExists(TurSEInstance seInstance, String indexName, String fieldName);

    // ---- Document operations (plugin resolves instance via site+locale) --

    boolean indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes);

    /**
     * Indexes a batch of documents in a single round-trip to the search engine
     * when the implementation supports it (Solr/Elasticsearch). The default
     * implementation falls back to per-document indexing — engines that don't
     * support batching get the same behaviour as before, but Solr/ES override
     * this to coalesce N documents into one HTTP/update request.
     *
     * @return number of documents successfully indexed.
     * @since 2026.2.7
     */
    default int indexDocuments(TurSNSite turSNSite, Locale locale,
            List<Map<String, Object>> documents) {
        int indexed = 0;
        for (Map<String, Object> doc : documents) {
            if (indexDocument(turSNSite, locale, doc)) {
                indexed++;
            }
        }
        return indexed;
    }

    boolean deIndex(TurSNSite turSNSite, Locale locale, String id);

    boolean deIndexByType(TurSNSite turSNSite, Locale locale, String type);

    boolean commit(TurSNSite turSNSite, Locale locale);

    // ---- Standalone index ops (T24b / §III.2 — RAG BM25 cores not tied to a TurSNSite) ----

    /**
     * T24b — index a document into a standalone index/core that doesn't
     * belong to any {@link TurSNSite}. Used by the RAG hybrid path to push
     * chunks into per-locale BM25 cores created via
     * {@link #createStandaloneIndex(TurSEInstance, Locale, String)}.
     *
     * <p>The default throws {@link UnsupportedOperationException} so a
     * plugin that hasn't implemented standalone indexing fails loudly at
     * runtime instead of silently dropping documents. Each plugin that
     * supports it (Solr, ES, embedded Lucene) overrides.
     *
     * @param seInstance the search engine instance hosting the core
     * @param indexName  the core/index name (e.g. {@code rag_a3b2c1d4_pt-BR})
     * @param attributes the document body, including a non-null {@code id}
     *                   field used for upsert/dedup
     * @return {@code true} when the document was accepted by the SE
     * @since 2026.2.7
     */
    default boolean indexStandaloneDocument(TurSEInstance seInstance, String indexName,
            Map<String, Object> attributes) {
        throw new UnsupportedOperationException(
                "indexStandaloneDocument not supported by " + getPluginType());
    }

    /**
     * T24b — batch version of {@link #indexStandaloneDocument} for the
     * RAG indexer's per-locale chunk push. Default implementation loops
     * over the single-doc method; plugins that benefit from a multi-doc
     * round-trip (Solr's update endpoint, ES's _bulk) should override.
     *
     * @return number of documents successfully indexed
     * @since 2026.2.7
     */
    default int indexStandaloneDocuments(TurSEInstance seInstance, String indexName,
            List<Map<String, Object>> documents) {
        int indexed = 0;
        for (Map<String, Object> doc : documents) {
            if (indexStandaloneDocument(seInstance, indexName, doc)) {
                indexed++;
            }
        }
        return indexed;
    }

    /**
     * T24b — delete a single document by id from a standalone index/core.
     * Used when an asset is unindexed (its chunks need to be removed from
     * every per-locale BM25 core that mirrors the vector store).
     *
     * @since 2026.2.7
     */
    default boolean deIndexStandalone(TurSEInstance seInstance, String indexName, String id) {
        throw new UnsupportedOperationException(
                "deIndexStandalone not supported by " + getPluginType());
    }

    /**
     * T24b — delete all documents in a standalone index/core matching a
     * field value. Used by the indexer's "reindex an asset" path: delete
     * all chunks with {@code assetId=<id>} before pushing the new ones,
     * so partial deletions / re-chunkings don't leave orphans.
     *
     * @since 2026.2.7
     */
    default boolean deIndexStandaloneByField(TurSEInstance seInstance, String indexName,
            String fieldName, String fieldValue) {
        throw new UnsupportedOperationException(
                "deIndexStandaloneByField not supported by " + getPluginType());
    }

    /**
     * T24b — flush pending writes to a standalone index/core. The standard
     * (SN site) {@link #commit(TurSNSite, Locale)} path inherits per-locale
     * routing from the site; standalone cores don't have that wrapper, so
     * we expose a sibling commit by index name. Plugins that auto-commit
     * (ES with refresh policy, Solr in soft-commit mode) can no-op.
     *
     * @return {@code true} on successful commit (or no-op for auto-commit)
     * @since 2026.2.7
     */
    default boolean commitStandalone(TurSEInstance seInstance, String indexName) {
        // No-op default — Solr explicit-commit and Lucene embedded
        // override; ES near-real-time refresh makes the commit step
        // unnecessary at this layer.
        return true;
    }

    /**
     * T24b — BM25 search over a standalone index/core. Returns the top
     * {@code topK} hits, sorted by descending relevance score (each
     * plugin's native BM25 — Lucene under Solr/ES uses
     * {@code k1=1.2, b=0.75} by default; admins can tune via the SE's
     * own config, see {@code docs/RAG_HYBRID_CONFIG.md} in Phase 5).
     *
     * <p>Used by {@code TurRagSearchToolService} on the SE-backed
     * production hybrid path: the tool dispatches to the per-locale core
     * (resolved via {@link com.viglet.turing.persistence.model.rag.TurRagBm25Core}),
     * gets a ranked list, and feeds it to the RRF fuser together with
     * the vector-store list.
     *
     * <p>{@code query} is a raw user query (Lucene QueryParser syntax NOT
     * exposed at this layer — plugins should escape special chars and
     * parse as a default-text-field BM25 search). Empty / null queries
     * return an empty list, not throw.
     *
     * <p>Default throws {@link UnsupportedOperationException} so a
     * plugin that hasn't implemented standalone search fails loudly at
     * runtime. Solr and Elasticsearch override; Lucene-embedded SE
     * doesn't (admins on that engine use the T24 base embedded path
     * via {@code TurLuceneVectorStore.hybridSearch}).
     *
     * @return up to {@code topK} hits, sorted by relevance descending;
     *         empty list when the query is blank or returns no matches
     * @since 2026.2.7
     */
    default List<TurSEStandaloneHit> retrieveStandalone(TurSEInstance seInstance, String indexName,
            String query, int topK) {
        throw new UnsupportedOperationException(
                "retrieveStandalone not supported by " + getPluginType());
    }

    /**
     * T28 / §III.5 — {@code MoreLikeThis} search over a standalone
     * index/core. Returns the top {@code topK} hits whose {@code field}
     * is most similar to the supplied {@code text}, scored by the SE's
     * native MLT implementation:
     *
     * <ul>
     *   <li>Solr: {@code /mlt} request handler with {@code stream.body}
     *       (requires admin opt-in via {@code enableStreamBody=true};
     *       falls back to a regular BM25 search against {@code field}
     *       when the handler isn't reachable).</li>
     *   <li>Elasticsearch: {@code more_like_this} query with the text as
     *       {@code like}.</li>
     *   <li>Lucene-embedded: not overridden — single-JVM admins use the
     *       {@code TurLuceneIntentClassifier} embedded strategy instead
     *       (T28 Phase A), so a no-op default keeps the contract simple.</li>
     * </ul>
     *
     * <p>Used by {@code TurSeMltIntentClassifier} on the SE-backed
     * production analytics path: it dispatches to the per-agent intent
     * index, gets the closest catalog labels, and returns the top match
     * as the session's intent label.
     *
     * <p>{@code text} is the raw user transcript — plugins MUST sanitize
     * before composing engine-specific queries (Solr's stream.body needs
     * no escaping; ES MLT takes it verbatim). Empty / null returns an
     * empty list, not throw.
     *
     * <p>Each {@link TurSEStandaloneHit#id() id} on the response is the
     * intent label (catalogs index one doc per label); the score is the
     * SE's native MLT relevance.
     *
     * @param field the indexed text field MLT scores against (typically
     *              {@code "samples"} for intent catalogs)
     * @return up to {@code topK} hits, sorted by relevance descending;
     *         empty list when the query is blank, the index is missing,
     *         or no docs cleared the MLT score threshold
     * @since 2026.3.1
     */
    default List<TurSEStandaloneHit> moreLikeThisStandalone(TurSEInstance seInstance,
            String indexName, String field, String text, int topK) {
        throw new UnsupportedOperationException(
                "moreLikeThisStandalone not supported by " + getPluginType());
    }

    // ---- Similar documents (T384) ----------------------------------------

    /**
     * T384 / §XX.4 — public "documents similar to this id" retrieval over a
     * site's live index. Returns up to {@code rows} documents most like the one
     * identified by {@code id}, scored by the engine's native MoreLikeThis
     * (Solr {@code /mlt} handler keyed by {@code id:<id>}, falling back to a BM25
     * disjunction over the seed's text; Elasticsearch {@code more_like_this} with
     * the seed text as {@code like}). The seed document itself is excluded.
     *
     * <p>This is the lexical half of the similar-documents seam; the semantic
     * (vector-neighbor) half lives in {@code TurSNHybridRankingService.findNeighbors}
     * and is orchestrated by {@code TurSNSimilarDocumentsService}.
     *
     * <p>Default returns an empty list so engines without a MoreLikeThis
     * capability (Lucene-embedded) degrade silently rather than throwing.
     *
     * @param siteLocale the (site, locale) index to query
     * @param id         the seed document id
     * @param rows       maximum number of similar documents to return
     * @return up to {@code rows} similar documents, seed excluded; empty when
     *         the id is blank, the index is missing, or nothing is similar
     * @since 2026.3.4
     */
    default List<com.viglet.turing.commons.se.similar.TurSESimilarResult> getSimilarDocuments(
            TurSNSiteLocale siteLocale, String id, int rows) {
        return List.of();
    }

    /**
     * T384 / §XX.4 — fetches the standard fields ({@code id}, {@code title},
     * {@code type}, {@code url}, {@code abstract}, {@code text}) for the given
     * document ids, preserving the input order and dropping ids that don't
     * resolve. Used to read a seed document's content for vector-neighbor
     * retrieval and to hydrate neighbor ids into renderable results.
     *
     * <p>Default returns an empty list.
     *
     * @param siteLocale the (site, locale) index to query
     * @param ids        the document ids to fetch
     * @return one field map per resolved id, in {@code ids} order
     * @since 2026.3.4
     */
    default List<Map<String, Object>> getDocumentsByIds(TurSNSiteLocale siteLocale, List<String> ids) {
        return List.of();
    }

    // ---- Synonyms (T663 / §XXXIX, Block AP) ------------------------------

    /**
     * T663 — whether this engine can apply <em>query-time</em> synonyms without
     * a reindex (Algolia's model). Solr (managed synonyms + reload) and
     * Elasticsearch (Synonyms API) return {@code true}; the default is
     * {@code false} so an engine that hasn't wired synonyms degrades honestly.
     */
    default boolean supportsSynonyms() {
        return false;
    }

    /**
     * T663 — pushes the (site, locale) synonym rules into the engine's
     * query-time analysis so a search for any term in a rule matches records
     * containing the equivalents, <em>without</em> reindexing. The rules are the
     * engine-neutral projection built by the synonym service from the
     * Turing-owned source of truth ({@code TurSNSynonym}).
     *
     * <p>Implementations MUST NOT silently drop a rule they can't represent —
     * they report it in {@link TurSESynonymApplyResult#unsupportedTypes()} (e.g.
     * {@code PLACEHOLDER} on Lucene) so the admin UI can show an honest
     * capability banner. Corrections degrade to one-way synonyms with a warning
     * on engines without a per-synonym typo budget.
     *
     * <p>The default returns {@link TurSESynonymApplyResult#unsupported} — an
     * engine that doesn't override advertises no synonym support rather than
     * throwing.
     *
     * @param seInstance the SE instance hosting the index/core
     * @param indexName  the target index/core name
     * @param locale     the locale the rules apply to
     * @param rules      the synonym rules to apply (replacing any prior set)
     * @return what was applied and what could not be represented
     */
    default TurSESynonymApplyResult applySynonyms(TurSEInstance seInstance, String indexName,
            Locale locale, List<TurSESynonymRule> rules) {
        return TurSESynonymApplyResult.unsupported(getPluginType());
    }

    // ---- Copy field (Solr-specific; no-op for schema-less engines) -------

    /**
     * Creates a copy field mapping if the engine supports it.
     * Lucene and Elasticsearch ignore this call by default.
     */
    default void createCopyField(TurSEInstance seInstance, String indexName, String fieldName,
            TurSEFieldType fieldType, boolean multiValued) {
        // No-op for schema-less engines
    }

    // ---- Content retrieval ------------------------------------------------

    /**
     * Retrieves a page of documents from the index for export purposes.
     * Only returns fields defined in the SN Site field map, respecting
     * multiValued settings (single-value fields are flattened from arrays).
     *
     * @param turSNSiteLocale  the site locale to query
     * @param siteFieldMap     field definitions from TurSNSiteFieldService.toMap()
     * @param start            pagination offset
     * @param rows             number of documents per page
     * @return list of documents as maps
     */
    default List<Map<String, Object>> retrieveDocumentPage(TurSNSiteLocale turSNSiteLocale,
            Map<String, ?> siteFieldMap, int start, int rows) {
        return List.of();
    }

    // ---- Autocomplete -----------------------------------------------------

    /**
     * Returns autocomplete suggestions for the given query term.
     * Implementations should return a list of suggested terms sorted by relevance.
     *
     * @param siteName the SN site name
     * @param term     the partial query to complete
     * @param locale   the locale for the search
     * @param rows     maximum number of suggestions to return
     * @return list of autocomplete suggestions
     */
    default List<String> autoComplete(String siteName, String term, Locale locale, long rows) {
        return List.of();
    }

    // ---- Spell check (T686 / §XLI.2, Block AR) ---------------------------

    /**
     * T686 / §XLI.2 — "did you mean" spell correction for the given query term
     * on a site's live index. Returns a
     * {@link com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult} whose
     * {@code corrected} flag is set (with a non-empty {@code correctedText})
     * only when the engine found a better-spelled alternative; otherwise an
     * uncorrected result.
     *
     * <p>Spell-check was originally hardwired to Solr
     * ({@code TurSolr.spellCheckTerm}), so on a Lucene site the "did you mean"
     * suggestion never appeared. This seam lets every engine expose the
     * capability behind one contract:
     * <ul>
     *   <li>Solr: delegates to the {@code /tur_spell} handler.</li>
     *   <li>Lucene-embedded: a {@code DirectSpellChecker} over the live index
     *       terms (no side dictionary to maintain).</li>
     *   <li>Engines without a spell-check capability keep the default and
     *       degrade silently to an uncorrected result rather than throwing.</li>
     * </ul>
     *
     * @param siteName the SN site name
     * @param term     the raw query term to spell-check
     * @param locale   the locale whose (site, locale) index to consult
     * @return the correction result; never {@code null}
     * @since 2026.3.4
     */
    default com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult spellCheck(
            String siteName, String term, Locale locale) {
        return new com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult();
    }

    // ---- Monitoring ------------------------------------------------------

    long getDocumentTotal(TurSNSiteLocale turSNSiteLocale);

    /**
     * T388 — counts the documents in the site's index (for the given locale)
     * that <em>populate</em> the named field, i.e. carry a non-null/non-empty
     * value for it. This is the numerator behind per-field coverage /
     * completeness observability: dividing by {@link #getDocumentTotal} yields
     * the fraction of documents a source actually fills in for that field.
     *
     * <p>It honours the T381 grounding invariant — an absent field lowers
     * coverage rather than being counted as a present empty value — by relying
     * on each engine's "field exists" primitive (Solr {@code field:[* TO *]},
     * Lucene {@code FieldExistsQuery}, Elasticsearch {@code exists}).
     *
     * <p>The default returns {@code -1}, meaning "this engine cannot report
     * field presence"; callers should treat a negative result as
     * <em>unknown</em> (not zero) so the UI can degrade gracefully instead of
     * reporting a misleading 0% coverage.
     *
     * @param turSNSiteLocale the site locale whose index/core to query
     * @param fieldName       the field whose presence to count
     * @return number of documents populating the field, or {@code -1} if the
     *         engine does not support field-presence counting
     * @since 2026.3.1
     */
    default long getDocumentCountWithField(TurSNSiteLocale turSNSiteLocale, String fieldName) {
        return -1L;
    }

    /**
     * T472 — counts the documents in the site's index (for the given locale)
     * whose numeric {@code fieldName} value is <em>strictly below</em>
     * {@code threshold}. This is the bucketing primitive behind the
     * audience-content-fit coverage report: counting documents whose
     * {@code content_fit_score} falls under the "too complex" / "borderline"
     * thresholds.
     *
     * <p>Implemented with each engine's exclusive-upper range query (Solr
     * {@code field:[* TO threshold}}, Elasticsearch {@code range lt}). The
     * default returns {@code -1}, meaning "this engine cannot report range
     * counts"; callers should treat a negative result as <em>unknown</em> and
     * degrade gracefully (mirrors {@link #getDocumentCountWithField}).
     *
     * @param turSNSiteLocale the site locale whose index/core to query
     * @param fieldName       the numeric field to range over
     * @param threshold       exclusive upper bound
     * @return number of documents with {@code fieldName < threshold}, or
     *         {@code -1} if the engine does not support range counting
     * @since 2026.3.4
     */
    default long getDocumentCountWithFieldBelow(TurSNSiteLocale turSNSiteLocale, String fieldName,
            int threshold) {
        return -1L;
    }

    /**
     * Returns system information about the search engine instance,
     * such as version, OS, JVM info, etc.
     * Keys and values are engine-specific.
     */
    default Map<String, String> getSystemInfo(TurSEInstance seInstance) {
        return Map.of("engine", getPluginType(), "status", "UP");
    }
}
