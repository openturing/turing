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
package com.viglet.turing.genai.provider.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.InitializingBean;

import lombok.extern.slf4j.Slf4j;

/**
 * Chroma {@link VectorStore} wrapper that adds two capabilities Spring AI's
 * {@link ChromaVectorStore} does not provide out of the box:
 *
 * <ol>
 *   <li><b>Filterable arrays.</b> Chroma's metadata schema is
 *       {@code Map<String, scalar>} — collection values get JSON-stringified
 *       (e.g. {@code type=["Animal","Plant"]} → {@code "[\"Animal\",\"Plant\"]"}),
 *       which makes {@code where: {type: "Animal"}} match nothing. This wrapper
 *       writes a sibling marker entry per array element ({@code typeRT__&lt;hash&gt; = "1"})
 *       at index time, so equality and IN filters can target each member
 *       individually.</li>
 *   <li><b>Translated {@link Filter.Expression}.</b> The
 *       {@link #similaritySearch(SearchRequest)} path overrides Spring AI's
 *       built-in filter translation when a request has a
 *       {@code filterExpression}, so the {@code where} clause uses the marker
 *       keys above.</li>
 * </ol>
 *
 * <p>Other operations ({@code delete}, {@code add} when no metadata
 * processing is needed, default {@code similaritySearch(String)}) are
 * delegated to the wrapped Spring AI store.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
public class TurChromaVectorStore implements VectorStore, InitializingBean {

    static final String MARKER_SEPARATOR = "__";
    private static final String MARKER_VALUE = "1";

    private final ChromaVectorStore delegate;
    private final ChromaApi chromaApi;
    private final EmbeddingModel embeddingModel;
    private final String tenantName;
    private final String databaseName;
    private final String collectionName;
    private volatile String collectionIdCache;

    public TurChromaVectorStore(ChromaVectorStore delegate,
            ChromaApi chromaApi,
            EmbeddingModel embeddingModel,
            String tenantName,
            String databaseName,
            String collectionName) {
        this.delegate = delegate;
        this.chromaApi = chromaApi;
        this.embeddingModel = embeddingModel;
        this.tenantName = tenantName;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @Override
    public String getName() {
        return "TurChromaVectorStore[" + collectionName + "]";
    }

    /**
     * Forwards Spring AI's {@link InitializingBean#afterPropertiesSet()} to
     * the wrapped {@link ChromaVectorStore} so the underlying collection is
     * created/loaded — without this, {@code doAdd} fails with
     * "collectionId should not be null". Triggered by
     * {@code TurRagContextBuilder.build} after the wrapper is constructed.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        delegate.afterPropertiesSet();
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Document> rewritten = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            rewritten.add(rewriteForIndex(doc));
        }
        delegate.add(rewritten);
    }

    @Override
    public void delete(List<String> idList) {
        delegate.delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        delegate.delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null || request.getFilterExpression() == null) {
            // No filter → Spring AI's standard path is fine (it embeds the
            // query and does plain similarity over the collection).
            return delegate.similaritySearch(request);
        }
        Map<String, Object> where;
        try {
            where = translateFilter(request.getFilterExpression());
        } catch (UnsupportedOperationException e) {
            log.warn("[Chroma] Unsupported filter expression — falling back to unfiltered similarity search: {}",
                    e.getMessage());
            return delegate.similaritySearch(request);
        }
        if (where == null) {
            // Filter resolved to no-op → unfiltered.
            return delegate.similaritySearch(SearchRequest.from(request).filterExpression((Filter.Expression) null).build());
        }
        try {
            float[] queryVector = embeddingModel.embed(request.getQuery());
            int topK = request.getTopK();
            ChromaApi.QueryRequest qReq = new ChromaApi.QueryRequest(
                    List.of(queryVector),
                    topK,
                    where,
                    List.of(ChromaApi.QueryRequest.Include.METADATAS,
                            ChromaApi.QueryRequest.Include.DOCUMENTS,
                            ChromaApi.QueryRequest.Include.DISTANCES));
            ChromaApi.QueryResponse resp = chromaApi.queryCollection(
                    tenantName, databaseName, requireCollectionId(), qReq);
            return mapResponse(resp, request.getSimilarityThreshold());
        } catch (Exception e) {
            log.error("[Chroma] Filtered similarity search failed for collection '{}': {}",
                    collectionName, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Adds {@code <field>__<sha1[:12]>=1} marker entries for every scalar value
     * (and every element of a collection value) so equality / IN filters can
     * target individual members of an array — Chroma's flat
     * {@code Map<String, scalar>} schema otherwise stringifies arrays into a
     * single value that no equality clause can pierce. The original keys are
     * preserved (collections joined with {@code ", "} for display), so the
     * existing facet aggregation, listChunks output and admin UI keep working.
     */
    private Document rewriteForIndex(Document doc) {
        Map<String, Object> original = doc.getMetadata();
        if (original.isEmpty()) {
            return doc;
        }
        Map<String, Object> rewritten = LinkedHashMap.newLinkedHashMap(original.size());
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            rewriteMetadataEntry(entry.getKey(), entry.getValue(), rewritten);
        }
        return Document.builder()
                .id(doc.getId())
                .text(doc.getText())
                .metadata(rewritten)
                .score(doc.getScore())
                .build();
    }

    /**
     * Rewrites one metadata entry into {@code rewritten}: collections are joined
     * to a CSV string plus a marker key per element; scalars (string/number/bool)
     * get a value + marker key; other types are copied verbatim. Null values and
     * empty collections are dropped.
     */
    private void rewriteMetadataEntry(String key, Object value, Map<String, Object> rewritten) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> col) {
            List<String> stringValues = new ArrayList<>(col.size());
            for (Object v : col) {
                if (v == null) {
                    continue;
                }
                stringValues.add(v.toString());
            }
            if (stringValues.isEmpty()) {
                return;
            }
            rewritten.put(key, String.join(", ", stringValues));
            for (String s : stringValues) {
                rewritten.put(markerKey(key, s), MARKER_VALUE);
            }
        } else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            rewritten.put(key, value);
            rewritten.put(markerKey(key, value.toString()), MARKER_VALUE);
        } else {
            // Complex / unknown type → leave as-is and let Spring AI / Chroma
            // decide. No marker generated since we can't filter on it anyway.
            rewritten.put(key, value);
        }
    }

    /**
     * Translates a Spring AI {@link Filter.Expression} into a Chroma
     * {@code where} clause that uses the marker-key convention.
     *
     * @return {@code null} when the expression resolves to no-op; throws
     *         {@link UnsupportedOperationException} for operators we cannot
     *         model with marker keys (currently: GT/GTE/LT/LTE — would
     *         require numeric metadata fields, which Chroma supports natively
     *         but Spring AI's filter translation already handles for scalars,
     *         so the caller is told to fall back to the default path).
     */
    @SuppressWarnings("java:S1168") // null is a no-op-filter sentinel: callers
    // (similaritySearch line ~146, combine line ~284) branch on null to mean
    // "match all / no clause on this side", which an empty map cannot express.
    Map<String, Object> translateFilter(Filter.Expression expression) {
        Filter.ExpressionType type = expression.type();
        switch (type) {
            case EQ -> {
                String key = extractKey(expression.left());
                Object value = extractValue(expression.right());
                if (key == null || value == null) {
                    return null;
                }
                return Map.of(markerKey(key, value.toString()), MARKER_VALUE);
            }
            case IN -> {
                return translateInFilter(expression);
            }
            case AND -> {
                return combine("$and", expression);
            }
            case OR -> {
                return combine("$or", expression);
            }
            case NE, NIN, GT, GTE, LT, LTE, ISNULL, ISNOTNULL, NOT ->
                throw new UnsupportedOperationException("Operator not supported: " + type);
            default -> throw new UnsupportedOperationException("Operator not supported: " + type);
        }
    }

    /** Translates an {@code IN} filter into a single marker map, an {@code $or} of markers, or null. */
    @SuppressWarnings("java:S1168") // null is the same no-op-filter sentinel as translateFilter; an empty map cannot express "no clause".
    private Map<String, Object> translateInFilter(Filter.Expression expression) {
        String key = extractKey(expression.left());
        List<Object> values = extractValueList(expression.right());
        if (key == null || values.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> ors = new ArrayList<>(values.size());
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            ors.add(Map.of(markerKey(key, v.toString()), MARKER_VALUE));
        }
        if (ors.isEmpty()) {
            return null;
        }
        if (ors.size() == 1) {
            return ors.get(0);
        }
        return Map.of("$or", ors);
    }

    private Map<String, Object> combine(String op, Filter.Expression expression) {
        Filter.Expression left = asExpression(expression.left());
        Filter.Expression right = asExpression(expression.right());
        Map<String, Object> leftMap = left != null ? translateFilter(left) : null;
        Map<String, Object> rightMap = right != null ? translateFilter(right) : null;
        if (leftMap == null) {
            return rightMap;
        }
        if (rightMap == null) {
            return leftMap;
        }
        return Map.of(op, List.of(leftMap, rightMap));
    }

    private Filter.Expression asExpression(Filter.Operand operand) {
        if (operand instanceof Filter.Expression expr) {
            return expr;
        }
        if (operand instanceof Filter.Group(var content)) {
            return content;
        }
        return null;
    }

    private String extractKey(Filter.Operand operand) {
        if (operand instanceof Filter.Key(var key)) {
            return key;
        }
        return null;
    }

    private Object extractValue(Filter.Operand operand) {
        if (operand instanceof Filter.Value(var value)) {
            return value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractValueList(Filter.Operand operand) {
        Object raw = extractValue(operand);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of(raw);
    }

    private List<Document> mapResponse(ChromaApi.QueryResponse resp, double similarityThreshold) {
        if (resp == null || resp.ids() == null || resp.ids().isEmpty()) {
            return List.of();
        }
        List<String> ids = resp.ids().get(0);
        List<String> docs = resp.documents() != null && !resp.documents().isEmpty()
                ? resp.documents().get(0)
                : List.of();
        List<Map<String, Object>> metas = resp.metadata() != null && !resp.metadata().isEmpty()
                ? resp.metadata().get(0)
                : List.of();
        List<Double> dists = resp.distances() != null && !resp.distances().isEmpty()
                ? resp.distances().get(0)
                : List.of();
        List<Document> result = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            Document doc = toDocument(i, ids, docs, metas, dists, similarityThreshold);
            if (doc != null) {
                result.add(doc);
            }
        }
        return result;
    }

    /**
     * Builds the {@code i}-th result {@link Document} (similarity = 1 − cosine
     * distance), or null when it falls below {@code similarityThreshold}.
     */
    private Document toDocument(int i, List<String> ids, List<String> docs,
            List<Map<String, Object>> metas, List<Double> dists, double similarityThreshold) {
        double distance = i < dists.size() && dists.get(i) != null ? dists.get(i) : 0.0;
        // Spring AI's convention: similarity = 1 - distance (cosine).
        double similarity = 1.0 - distance;
        if (similarity < similarityThreshold) {
            return null;
        }
        String content = i < docs.size() ? docs.get(i) : "";
        Map<String, Object> metadata = i < metas.size() && metas.get(i) != null
                ? metas.get(i)
                : Map.of();
        return Document.builder()
                .id(ids.get(i))
                .text(content == null ? "" : content)
                .metadata(stripMarkers(metadata))
                .score(similarity)
                .build();
    }

    /**
     * Drops the internal {@code __<hash>} marker entries when handing
     * documents back to callers — keeps the public metadata clean.
     */
    private Map<String, Object> stripMarkers(Map<String, Object> metadata) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (var entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.contains(MARKER_SEPARATOR + "00")
                    || isMarkerKey(key)) {
                continue;
            }
            clean.put(key, entry.getValue());
        }
        return clean;
    }

    private static boolean isMarkerKey(String key) {
        int idx = key.lastIndexOf(MARKER_SEPARATOR);
        if (idx < 0 || idx + MARKER_SEPARATOR.length() + 12 != key.length()) {
            return false;
        }
        // Suffix must be exactly 12 hex chars (sha1[:12]).
        for (int i = idx + MARKER_SEPARATOR.length(); i < key.length(); i++) {
            char c = key.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private String requireCollectionId() {
        String cached = collectionIdCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (collectionIdCache != null) {
                return collectionIdCache;
            }
            ChromaApi.Collection collection = chromaApi.getCollection(tenantName, databaseName, collectionName);
            if (collection == null) {
                throw new IllegalStateException("Chroma collection '" + collectionName + "' not found");
            }
            collectionIdCache = collection.id();
            return collectionIdCache;
        }
    }

    /**
     * Builds the marker key for {@code field=value}: {@code <field>__<sha1[:12]>}.
     * Hashing the value is necessary because metadata values may contain
     * characters that would either confuse Chroma's where-clause parser
     * (spaces, brackets) or overflow Chroma's metadata key size limit.
     */
    static String markerKey(String field, String value) {
        return field + MARKER_SEPARATOR + sha1Hex12(value);
    }

    private static String sha1Hex12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i] & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-1 is mandated by the JDK, but fall back to hashCode just in case.
            int h = s == null ? 0 : s.hashCode();
            return String.format("%012x", h & 0xffffffffL);
        }
    }
}
