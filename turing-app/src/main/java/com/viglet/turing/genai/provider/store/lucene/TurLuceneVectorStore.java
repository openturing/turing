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
package com.viglet.turing.genai.provider.store.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI {@link VectorStore} implementation backed by Apache Lucene KNN.
 * <p>
 * Stores each document as a Lucene document with:
 * <ul>
 *   <li>{@code id} — indexed {@link StringField} for lookup and delete-by-id</li>
 *   <li>{@code content} — {@link StoredField} with the document text</li>
 *   <li>{@code embedding} — {@link KnnFloatVectorField} with cosine similarity</li>
 *   <li>{@code metadata_json} — {@link StoredField} with serialized metadata</li>
 *   <li>{@code meta_&lt;key&gt;} — indexed {@link StringField} per scalar metadata entry (for filters)</li>
 * </ul>
 * <p>
 * The underlying Lucene {@link IndexWriter} and filesystem directory are managed by
 * {@link TurLuceneVectorIndex} and shared via the provider's cache. This class is thread-safe:
 * Lucene's {@code IndexWriter} is thread-safe, and reader refresh is synchronized in the index handle.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
public class TurLuceneVectorStore implements VectorStore {

    public static final String FIELD_ID = "id";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_EMBEDDING = "embedding";
    public static final String FIELD_METADATA_JSON = "metadata_json";
    public static final String METADATA_FIELD_PREFIX = "meta_";

    /**
     * T19 / §III.3: metadata key the {@link #bm25Fallback(String, int)} path
     * sets on every {@link Document} it emits so downstream prompt builders
     * can warn the LLM that the match came from keyword search, not the
     * embedding model. Callers should NOT set this themselves on regular
     * vector hits — the absence of the key is the "this came from vector"
     * signal.
     *
     * <p>T24 / §III.2: same key is reused by the hybrid path
     * ({@link #hybridSearch(String, int, double)}) to mark per-document
     * which results came ONLY from BM25 (vector didn't agree). When EVERY
     * hybrid result carries the tag, the prompt builder treats the entire
     * response as keyword-only; when at least one came from vector, the
     * prompt drops the global warning and just trusts the fusion ranking.
     *
     * <p>Lives on {@code Document.metadata} (not stored in Lucene) — this is
     * a per-result tag, not part of the indexed schema.
     *
     * @since 2026.2.7
     */
    public static final String METADATA_KEYWORD_ONLY_FALLBACK = "_keyword_only_fallback";

    /**
     * T24 / §III.2 — Reciprocal Rank Fusion constant. {@code k=60} is the
     * canonical default from the original RRF paper (Cormack et al.) and
     * the value Elasticsearch / Solr ship in their native hybrid query
     * implementations. Smaller {@code k} sharpens the contribution of
     * top-ranked docs; larger {@code k} flattens the curve so docs
     * further down the list still contribute.
     */
    private static final int RRF_K = 60;
    /**
     * Prefix for numeric {@link DoublePoint} fields built from numeric metadata values.
     * Used for {@code GT/GTE/LT/LTE} range filtering; the string form is still indexed under
     * {@link #METADATA_FIELD_PREFIX} for equality.
     */
    public static final String METADATA_NUMERIC_FIELD_PREFIX = "metan_";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final TurLuceneVectorIndex index;
    private final EmbeddingModel embeddingModel;
    private final String collectionName;

    public TurLuceneVectorStore(TurLuceneVectorIndex index,
            EmbeddingModel embeddingModel,
            String collectionName) {
        this.index = index;
        this.embeddingModel = embeddingModel;
        this.collectionName = collectionName;
    }

    @Override
    public String getName() {
        return "LuceneVectorStore[" + collectionName + "]";
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            IndexWriter writer = index.getWriter();
            for (Document document : documents) {
                float[] vector = embedDocument(document);
                org.apache.lucene.document.Document luceneDoc = toLuceneDocument(document, vector);
                writer.updateDocument(new Term(FIELD_ID, document.getId()), luceneDoc);
            }
            writer.commit();
            log.debug("[LuceneVectorStore] Added {} document(s) to collection '{}'",
                    documents.size(), collectionName);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to add documents to Lucene vector store '" + collectionName + "'", e);
        }
    }

    @Override
    public void delete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        try {
            IndexWriter writer = index.getWriter();
            Term[] terms = idList.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> new Term(FIELD_ID, id))
                    .toArray(Term[]::new);
            if (terms.length > 0) {
                writer.deleteDocuments(terms);
                writer.commit();
                log.debug("[LuceneVectorStore] Deleted {} id(s) from collection '{}'",
                        terms.length, collectionName);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to delete documents from Lucene vector store '" + collectionName + "'", e);
        }
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        if (filterExpression == null) {
            return;
        }
        try {
            IndexWriter writer = index.getWriter();
            Query filterQuery = toLuceneFilterQuery(filterExpression);
            if (filterQuery == null) {
                log.warn("[LuceneVectorStore] Unsupported filter expression for delete; no-op");
                return;
            }
            writer.deleteDocuments(filterQuery);
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to delete by filter from Lucene vector store '" + collectionName + "'", e);
        }
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null) {
            return List.of();
        }
        String query = request.getQuery();
        if (query.isBlank()) {
            return List.of();
        }
        int topK = Math.max(1, request.getTopK());
        double threshold = request.getSimilarityThreshold();
        float[] queryVector;
        try {
            queryVector = embeddingModel.embed(query);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to embed query for Lucene vector store '" + collectionName + "'", e);
        }
        Query filterQuery = request.getFilterExpression() != null
                ? toLuceneFilterQuery(request.getFilterExpression())
                : null;
        try {
            IndexSearcher searcher = index.getSearcher();
            if (searcher.getIndexReader().numDocs() == 0) {
                return List.of();
            }
            KnnFloatVectorQuery knnQuery = filterQuery != null
                    ? new KnnFloatVectorQuery(FIELD_EMBEDDING, queryVector, topK, filterQuery)
                    : new KnnFloatVectorQuery(FIELD_EMBEDDING, queryVector, topK);
            TopDocs topDocs = searcher.search(knnQuery, topK);
            List<Document> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc hit : topDocs.scoreDocs) {
                if (hit.score < threshold) {
                    continue;
                }
                Document doc = toSpringAiDocument(searcher, hit);
                if (doc != null) {
                    results.add(doc);
                }
            }
            return results;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to run similarity search on Lucene vector store '" + collectionName + "'", e);
        }
    }

    /**
     * T24 / §III.2 — hybrid retrieval combining vector KNN + BM25 over
     * {@link #FIELD_CONTENT} via Reciprocal Rank Fusion. Strictly more
     * recall than vector-only because rare/out-of-distribution terms
     * (proper nouns, internal codes, SKUs, version strings) that the
     * embedding model treats as {@code <UNK>} get matched by BM25
     * instead, while semantically-aligned-but-different-wording docs the
     * keyword path misses still get matched by vector.
     *
     * <h2>RRF math</h2>
     *
     * For each document {@code d} that appears in either the vector or
     * BM25 result lists:
     * <pre>
     *   score(d) = Σ 1 / ({@link #RRF_K} + rank_i(d))
     * </pre>
     * where {@code rank_i(d)} is {@code d}'s 1-based rank in the i-th
     * retrieval system. Docs in BOTH lists get the sum of two reciprocals
     * (boost); docs in only one get one term. The final order is by
     * descending RRF score.
     *
     * <p><b>Tagging</b>: returned documents carry
     * {@link #METADATA_KEYWORD_ONLY_FALLBACK}{@code =true} only when they
     * appeared in BM25 and NOT in the vector hits. Vector hits — even
     * weak ones — never get the keyword-only flag because the embedding
     * model gave them at least some signal. Downstream prompt builders
     * use this per-doc tag to decide whether to globally warn the LLM
     * ("all results are keyword matches; verify carefully") or just
     * trust the fusion.
     *
     * <p><b>Over-fetch</b>: each underlying retriever is asked for
     * {@code max(topK, 10)} candidates so the fusion has enough material
     * to do real ranking instead of just emitting whichever list happens
     * to have more entries. The function returns the top {@code topK}
     * after fusion.
     *
     * @param query                       the natural-language query
     * @param topK                        max documents to return after fusion
     * @param vectorSimilarityThreshold   passed through to the vector pass
     *                                    so super-cold vector matches can
     *                                    still be filtered out; BM25 has
     *                                    no equivalent threshold (its
     *                                    score domain is unbounded)
     * @return up to {@code topK} {@link Document}s ranked by RRF score
     * @since 2026.2.7
     */
    public List<Document> hybridSearch(String query, int topK, double vectorSimilarityThreshold) {
        if (query == null || query.isBlank() || topK < 1) {
            return List.of();
        }
        int fetchK = Math.max(topK, 10);

        // Vector pass — use the same similaritySearch entry point as
        // existing callers so the embedding round-trip, filter handling,
        // and threshold behavior stay identical.
        SearchRequest vectorRequest = SearchRequest.builder()
                .query(query)
                .topK(fetchK)
                .similarityThreshold(vectorSimilarityThreshold)
                .build();
        List<Document> vectorHits;
        try {
            vectorHits = similaritySearch(vectorRequest);
        } catch (RuntimeException e) {
            // Defensive: if the vector pass fails (e.g. embedding service
            // outage), keep going with BM25 alone instead of failing the
            // whole search. Logged at WARN so the failure is visible.
            log.warn("[LuceneVectorStore] hybrid vector pass failed for '{}': {}", query, e.getMessage());
            vectorHits = List.of();
        }
        List<Document> bm25Hits = bm25Fallback(query, fetchK);
        return reciprocalRankFusion(vectorHits, bm25Hits, topK);
    }

    /**
     * T24 / §III.2 — implements the Reciprocal Rank Fusion algorithm
     * described on {@link #hybridSearch(String, int, double)}. Strips the
     * {@link #METADATA_KEYWORD_ONLY_FALLBACK} tag from BM25 docs that
     * ALSO appeared in {@code vectorHits} (vector confirmed → not
     * keyword-only) and preserves it for docs only present in
     * {@code bm25Hits}.
     */
    private static List<Document> reciprocalRankFusion(List<Document> vectorHits,
            List<Document> bm25Hits, int topK) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, Document> sourceDocs = new LinkedHashMap<>();
        java.util.Set<String> vectorIds = new java.util.HashSet<>(vectorHits.size());

        for (int i = 0; i < vectorHits.size(); i++) {
            Document d = vectorHits.get(i);
            if (d == null) {
                continue;
            }
            vectorIds.add(d.getId());
            rrfScores.merge(d.getId(), 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
            sourceDocs.putIfAbsent(d.getId(), d);
        }
        for (int i = 0; i < bm25Hits.size(); i++) {
            Document d = bm25Hits.get(i);
            if (d == null) {
                continue;
            }
            rrfScores.merge(d.getId(), 1.0 / (RRF_K + i + 1), (a, b) -> a + b);
            sourceDocs.putIfAbsent(d.getId(), d);
        }
        if (rrfScores.isEmpty()) {
            return List.of();
        }
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> rebuildWithFusedScore(sourceDocs.get(e.getKey()), e.getValue(),
                        vectorIds.contains(e.getKey())))
                .toList();
    }

    /**
     * Copies {@code source} into a new {@link Document} with the supplied
     * RRF score and a corrected {@link #METADATA_KEYWORD_ONLY_FALLBACK}
     * tag: removed when {@code wasInVector} is true (vector confirmed the
     * doc, regardless of whether BM25 also picked it), kept when only
     * BM25 found it.
     */
    private static Document rebuildWithFusedScore(Document source, double rrfScore, boolean wasInVector) {
        if (source == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(source.getMetadata());
        if (wasInVector) {
            metadata.remove(METADATA_KEYWORD_ONLY_FALLBACK);
        }
        return Document.builder()
                .id(source.getId())
                .text(source.getText())
                .metadata(metadata)
                .score(rrfScore)
                .build();
    }

    /**
     * T19 / §III.3 — emergency BM25 search over {@link #FIELD_CONTENT} for
     * when the vector path returns nothing useful. Caller decides whether
     * to fire this (typically: vector top-1 score below
     * {@code MIN_VECTOR_CONFIDENCE}, or vector returned zero hits despite a
     * non-empty corpus). The returned documents carry
     * {@link #METADATA_KEYWORD_ONLY_FALLBACK}{@code =true} so the prompt
     * builder can warn the LLM that the match is lexical, not semantic.
     *
     * <p><b>Query parsing</b>: uses Lucene's classic {@link QueryParser}
     * over a {@link StandardAnalyzer} (same analyzer the index writer used —
     * see {@link TurLuceneVectorIndex#TurLuceneVectorIndex}). Special
     * characters in the user's message are escaped so a query like
     * {@code "What does (foo) mean?"} doesn't blow up the parser; this also
     * means it falls back to plain term-OR matching, not the QueryParser
     * boolean syntax — appropriate for end-user text.
     *
     * <p><b>Older chunks limitation</b>: chunks indexed before T19 used
     * {@code StoredField} for {@link #FIELD_CONTENT} (no inverted index),
     * so they are invisible to this fallback. Vector search still finds
     * them. A full re-index is required to make them keyword-searchable.
     *
     * @param query   the natural-language query (already user-facing text)
     * @param topK    maximum number of documents to return ({@code >= 1})
     * @return up to {@code topK} {@link Document}s, ordered by BM25 score
     *         descending. Empty when the corpus contains no matching terms.
     * @since 2026.2.7
     */
    public List<Document> bm25Fallback(String query, int topK) {
        if (query == null || query.isBlank() || topK < 1) {
            return List.of();
        }
        String escaped = QueryParser.escape(query.trim());
        try {
            IndexSearcher searcher = index.getSearcher();
            if (searcher.getIndexReader().numDocs() == 0) {
                return List.of();
            }
            QueryParser parser = new QueryParser(FIELD_CONTENT, new StandardAnalyzer());
            // OR is the right default for natural-language queries: each
            // term contributes BM25 score, the best-overall doc wins. The
            // alternative (AND) would silently return zero hits when one
            // term doesn't appear, which is the exact failure mode we are
            // trying to avoid.
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query parsedQuery;
            try {
                parsedQuery = parser.parse(escaped);
            } catch (ParseException pe) {
                log.warn("[LuceneVectorStore] BM25 fallback failed to parse query '{}' "
                                + "(escaped='{}'): {}",
                        query, escaped, pe.getMessage());
                return List.of();
            }
            TopDocs topDocs = searcher.search(parsedQuery, topK);
            List<Document> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc hit : topDocs.scoreDocs) {
                Document doc = toSpringAiDocument(searcher, hit);
                if (doc == null) {
                    continue;
                }
                // Tag the result so the prompt builder downstream knows
                // this came from keyword search (the vector model never
                // weighed in). Need a fresh metadata map because the
                // Spring AI Document API returns an immutable view from
                // the builder.
                Map<String, Object> augmentedMetadata = new LinkedHashMap<>(doc.getMetadata());
                augmentedMetadata.put(METADATA_KEYWORD_ONLY_FALLBACK, Boolean.TRUE);
                results.add(Document.builder()
                        .id(doc.getId())
                        .text(doc.getText())
                        .metadata(augmentedMetadata)
                        .score(doc.getScore())
                        .build());
            }
            log.info("[LuceneVectorStore] BM25 fallback for query '{}' returned {} hit(s)",
                    query, results.size());
            return results;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to run BM25 fallback on Lucene vector store '" + collectionName + "'", e);
        }
    }

    private float[] embedDocument(Document document) {
        try {
            return embeddingModel.embed(document);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to embed document '" + document.getId() + "' for collection '" + collectionName + "'",
                    e);
        }
    }

    private org.apache.lucene.document.Document toLuceneDocument(Document document, float[] vector) {
        org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
        luceneDoc.add(new StringField(FIELD_ID, document.getId(), Field.Store.YES));
        String text = document.getText();
        if (text != null) {
            // T19 / §III.3: TextField indexes the content for BM25 search AND
            // stores the raw text for retrieval, replacing the legacy
            // StoredField (storage-only). New chunks indexed after T19 ships
            // are searchable by the BM25 fallback path; older chunks need a
            // re-index before they can be matched by keyword queries
            // (silently invisible to {@link #bm25Fallback}; vector search
            // still works on them).
            luceneDoc.add(new TextField(FIELD_CONTENT, text, Field.Store.YES));
        }
        luceneDoc.add(new KnnFloatVectorField(FIELD_EMBEDDING, vector,
                org.apache.lucene.index.VectorSimilarityFunction.COSINE));

        Map<String, Object> metadata = document.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                addMetadataFilterField(luceneDoc, entry.getKey(), entry.getValue());
            }
            try {
                String json = OBJECT_MAPPER.writeValueAsString(metadata);
                luceneDoc.add(new StoredField(FIELD_METADATA_JSON, json));
            } catch (Exception e) {
                log.warn("[LuceneVectorStore] Failed to serialize metadata for document '{}': {}",
                        document.getId(), e.getMessage());
            }
        }
        return luceneDoc;
    }

    private void addMetadataFilterField(org.apache.lucene.document.Document luceneDoc,
            String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        String stringField = METADATA_FIELD_PREFIX + key;
        String numericField = METADATA_NUMERIC_FIELD_PREFIX + key;
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addScalarMetadata(luceneDoc, stringField, numericField, item);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (Object item : (Object[]) value) {
                addScalarMetadata(luceneDoc, stringField, numericField, item);
            }
            return;
        }
        addScalarMetadata(luceneDoc, stringField, numericField, value);
    }

    private void addScalarMetadata(org.apache.lucene.document.Document luceneDoc,
            String stringField, String numericField, Object item) {
        if (item == null) {
            return;
        }
        luceneDoc.add(new StringField(stringField, String.valueOf(item), Field.Store.NO));
        Double numeric = toDouble(item);
        if (numeric != null) {
            luceneDoc.add(new DoublePoint(numericField, numeric));
        }
    }

    /**
     * Best-effort numeric coercion used by range filters. Booleans are intentionally not treated
     * as numeric; non-parseable strings fall back to {@code null} so callers use the lexicographic
     * string path instead.
     */
    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean) {
            return null;
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Document toSpringAiDocument(IndexSearcher searcher, ScoreDoc hit) throws IOException {
        org.apache.lucene.document.Document luceneDoc = searcher.storedFields().document(hit.doc);
        String id = luceneDoc.get(FIELD_ID);
        if (id == null) {
            return null;
        }
        String content = luceneDoc.get(FIELD_CONTENT);
        Map<String, Object> metadata = readMetadata(luceneDoc.get(FIELD_METADATA_JSON));
        return Document.builder()
                .id(id)
                .text(content != null ? content : "")
                .metadata(metadata)
                .score((double) hit.score)
                .build();
    }

    private Map<String, Object> readMetadata(String json) {
        return parseMetadata(json);
    }

    /**
     * Parses a stored {@code metadata_json} value into a map. Never throws — malformed content
     * yields an empty map (logged at debug level). Visible to collaborators (e.g. the provider's
     * collection-count logic) to avoid duplicating the JSON handling.
     */
    static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> metadata = OBJECT_MAPPER.readValue(json, METADATA_TYPE);
            return metadata != null ? metadata : new HashMap<>();
        } catch (Exception e) {
            log.debug("[LuceneVectorStore] Failed to deserialize metadata: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Converts a Spring AI {@link Filter.Expression} to a Lucene {@link Query}.
     * Supports EQ / NE on scalar metadata, IN / NIN, AND / OR / NOT composition.
     * Returns {@code null} when the expression contains unsupported operators.
     */
    Query toLuceneFilterQuery(Filter.Expression expression) {
        if (expression == null) {
            return new MatchAllDocsQuery();
        }
        Filter.ExpressionType type = expression.type();
        switch (type) {
            case EQ -> {
                return buildTermQuery(expression);
            }
            case NE -> {
                Query termQuery = buildTermQuery(expression);
                if (termQuery == null) {
                    return null;
                }
                BooleanQuery.Builder not = new BooleanQuery.Builder();
                not.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                not.add(termQuery, BooleanClause.Occur.MUST_NOT);
                return not.build();
            }
            case IN -> {
                return buildInQuery(expression, false);
            }
            case NIN -> {
                return buildInQuery(expression, true);
            }
            case GT -> {
                return buildRangeQuery(expression, false, false);
            }
            case GTE -> {
                return buildRangeQuery(expression, false, true);
            }
            case LT -> {
                return buildRangeQuery(expression, true, false);
            }
            case LTE -> {
                return buildRangeQuery(expression, true, true);
            }
            case ISNULL -> {
                return buildExistsQuery(expression, true);
            }
            case ISNOTNULL -> {
                return buildExistsQuery(expression, false);
            }
            case AND -> {
                return buildBooleanQuery(expression, BooleanClause.Occur.MUST);
            }
            case OR -> {
                return buildBooleanQuery(expression, BooleanClause.Occur.SHOULD);
            }
            case NOT -> {
                if (!(expression.left() instanceof Filter.Expression inner)) {
                    return null;
                }
                Query innerQuery = toLuceneFilterQuery(inner);
                if (innerQuery == null) {
                    return null;
                }
                BooleanQuery.Builder not = new BooleanQuery.Builder();
                not.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                not.add(innerQuery, BooleanClause.Occur.MUST_NOT);
                return not.build();
            }
            default -> {
                log.warn("[LuceneVectorStore] Unsupported filter operator '{}'", type);
                return null;
            }
        }
    }

    private Query buildBooleanQuery(Filter.Expression expression, BooleanClause.Occur occur) {
        Filter.Expression left = asExpression(expression.left());
        Filter.Expression right = asExpression(expression.right());
        Query leftQuery = left != null ? toLuceneFilterQuery(left) : null;
        Query rightQuery = right != null ? toLuceneFilterQuery(right) : null;
        if (leftQuery == null || rightQuery == null) {
            return null;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(leftQuery, occur);
        builder.add(rightQuery, occur);
        if (occur == BooleanClause.Occur.SHOULD) {
            builder.setMinimumNumberShouldMatch(1);
        }
        return builder.build();
    }

    private Filter.Expression asExpression(Filter.Operand operand) {
        if (operand instanceof Filter.Expression expr) {
            return expr;
        }
        if (operand instanceof Filter.Group group) {
            return group.content();
        }
        return null;
    }

    private Query buildTermQuery(Filter.Expression expression) {
        String key = extractKey(expression.left());
        Object value = extractValue(expression.right());
        if (key == null || value == null) {
            return null;
        }
        return new TermQuery(new Term(METADATA_FIELD_PREFIX + key, String.valueOf(value)));
    }

    /**
     * Builds a range query for {@code GT/GTE/LT/LTE}. Prefers {@link DoublePoint#newRangeQuery}
     * when the bound is numeric (documents indexed with {@link #METADATA_NUMERIC_FIELD_PREFIX});
     * otherwise falls back to a lexicographic {@link TermRangeQuery} on the string field — which
     * still works for ISO-8601 dates and for zero-padded strings.
     *
     * @param lessThan  {@code true} for LT/LTE, {@code false} for GT/GTE
     * @param inclusive whether the bound itself is included in the range
     */
    private Query buildRangeQuery(Filter.Expression expression, boolean lessThan, boolean inclusive) {
        String key = extractKey(expression.left());
        Object value = extractValue(expression.right());
        if (key == null || value == null) {
            return null;
        }
        Double numeric = toDouble(value);
        if (numeric != null) {
            String numericField = METADATA_NUMERIC_FIELD_PREFIX + key;
            double lower;
            double upper;
            if (lessThan) {
                lower = Double.NEGATIVE_INFINITY;
                upper = inclusive ? numeric : Math.nextDown(numeric);
            } else {
                lower = inclusive ? numeric : Math.nextUp(numeric);
                upper = Double.POSITIVE_INFINITY;
            }
            return DoublePoint.newRangeQuery(numericField, lower, upper);
        }
        String stringField = METADATA_FIELD_PREFIX + key;
        BytesRef bound = new BytesRef(String.valueOf(value));
        if (lessThan) {
            return new TermRangeQuery(stringField, null, bound, true, inclusive);
        }
        return new TermRangeQuery(stringField, bound, null, inclusive, true);
    }

    /**
     * Builds a field-exists / field-absent query. Implemented as an unbounded
     * {@link TermRangeQuery} over the string field (matches docs with any term in that field),
     * optionally negated against {@link MatchAllDocsQuery} for {@code ISNULL}.
     *
     * @param negate {@code true} for ISNULL (field absent), {@code false} for ISNOTNULL (field present)
     */
    private Query buildExistsQuery(Filter.Expression expression, boolean negate) {
        String key = extractKey(expression.left());
        if (key == null) {
            return null;
        }
        Query exists = new TermRangeQuery(METADATA_FIELD_PREFIX + key, null, null, true, true);
        if (!negate) {
            return exists;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        builder.add(exists, BooleanClause.Occur.MUST_NOT);
        return builder.build();
    }

    private Query buildInQuery(Filter.Expression expression, boolean negate) {
        String key = extractKey(expression.left());
        Object rawValue = extractValue(expression.right());
        if (key == null || rawValue == null) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            values.addAll(collection);
        } else if (rawValue.getClass().isArray()) {
            for (Object v : (Object[]) rawValue) {
                values.add(v);
            }
        } else {
            values.add(rawValue);
        }
        if (values.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder should = new BooleanQuery.Builder();
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            should.add(new TermQuery(new Term(METADATA_FIELD_PREFIX + key, String.valueOf(v))),
                    BooleanClause.Occur.SHOULD);
        }
        should.setMinimumNumberShouldMatch(1);
        Query inQuery = should.build();
        if (!negate) {
            return inQuery;
        }
        BooleanQuery.Builder not = new BooleanQuery.Builder();
        not.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        not.add(inQuery, BooleanClause.Occur.MUST_NOT);
        return not.build();
    }

    private String extractKey(Filter.Operand operand) {
        if (operand instanceof Filter.Key key) {
            return key.key();
        }
        return null;
    }

    private Object extractValue(Filter.Operand operand) {
        if (operand instanceof Filter.Value value) {
            return value.value();
        }
        return null;
    }

    /**
     * Returns an unmodifiable, insertion-ordered view of the public field names used by this store,
     * useful for tests and debugging.
     */
    public static Map<String, String> publicFieldNames() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", FIELD_ID);
        map.put("content", FIELD_CONTENT);
        map.put("embedding", FIELD_EMBEDDING);
        map.put("metadata", FIELD_METADATA_JSON);
        map.put("metaPrefix", METADATA_FIELD_PREFIX);
        return Map.copyOf(map);
    }

    /**
     * @return locale-insensitive collection name identifier (for logging).
     */
    public String collectionId() {
        return collectionName.toLowerCase(Locale.ROOT);
    }
}
