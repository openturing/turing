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
package com.viglet.turing.contentai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.passage.TurSEPassage;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * T1031 / §XX.18 — reads a {@code ContentSourceSearchResponse} back into
 * Turing's result types.
 *
 * <p>Adobe returns the ingested document under {@code data} with the identity and
 * the relevance score beside it, so a Turing result's field map is
 * {@code data} plus {@code id} and {@code score}. The {@code id} is written
 * <em>last</em> on purpose: it is the identity Content AI will answer about, so a
 * document whose own body happens to carry an {@code id} field must not shadow
 * it, or every follow-up lookup addresses something that does not exist.
 *
 * <p>The aggregate reader accepts both {@code buckets} and {@code values} for the
 * bucket array. Adobe's own schema disagrees with itself there —
 * {@code TermBucketResultData} declares {@code values} required while defining a
 * {@code buckets} property, and publishes no example response for aggregates —
 * so picking one and being wrong is a facet row that silently never appears.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurContentAiResponseMapper {

    private static final String NODE_RESULTS = "results";
    private static final String NODE_DATA = "data";
    private static final String NODE_AGGREGATES = "aggregates";
    private static final String NODE_BUCKETS = "buckets";
    private static final String NODE_VALUES = "values";
    private static final String NODE_TOTAL = "totalResults";
    private static final String NODE_CHUNKS = "chunks";
    private static final String NODE_CHUNK_DATA = "chunkData";
    private static final String NODE_TEXT = "text";
    private static final String NODE_CURSOR = "cursor";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_LABEL = "label";
    private static final String FIELD_COUNT = "count";

    /** {@code totalResults}, or 0 when the response omits it. */
    public long totalResults(JsonNode response) {
        return response != null && response.has(NODE_TOTAL) ? response.get(NODE_TOTAL).asLong(0L)
                : 0L;
    }

    /** The cursor for the next page, or {@code null} when this was the last one. */
    public String cursor(JsonNode response) {
        if (response == null || !response.has(NODE_CURSOR)) {
            return null;
        }
        String cursor = response.get(NODE_CURSOR).asString();
        return cursor != null && !cursor.isBlank() ? cursor : null;
    }

    /** One {@link TurSEResult} per hit, in the order Adobe ranked them. */
    public List<TurSEResult> results(JsonNode response) {
        List<TurSEResult> results = new ArrayList<>();
        if (response == null || !response.has(NODE_RESULTS)) {
            return results;
        }
        for (JsonNode hit : response.get(NODE_RESULTS)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            JsonNode data = hit.get(NODE_DATA);
            if (data != null && data.isObject()) {
                for (Map.Entry<String, JsonNode> entry : data.properties()) {
                    fields.put(entry.getKey(), toJavaValue(entry.getValue()));
                }
            }
            if (hit.has(FIELD_SCORE)) {
                fields.put(FIELD_SCORE, hit.get(FIELD_SCORE).asDouble());
            }
            if (hit.has(TurSNFieldName.ID)) {
                fields.put(TurSNFieldName.ID, hit.get(TurSNFieldName.ID).asString());
            }
            results.add(TurSEResult.builder()
                    .fields(fields)
                    .passages(passages(hit))
                    .build());
        }
        return results;
    }

    /**
     * T1044 / §XX.21 — the document's matched chunks as passages, highest score
     * first. Empty (not {@code null}) when Adobe returned {@code "chunks": []},
     * which it does for a document that matched lexically rather than
     * semantically; {@code null} when the request never asked for chunks, so a
     * caller can tell "not requested" from "none matched".
     */
    private List<TurSEPassage> passages(JsonNode hit) {
        JsonNode chunks = hit.get(NODE_CHUNKS);
        if (chunks == null || !chunks.isArray()) {
            return null;
        }
        List<TurSEPassage> passages = new ArrayList<>();
        for (JsonNode chunk : chunks) {
            JsonNode id = chunk.get(TurSNFieldName.ID);
            Double score = chunk.has(FIELD_SCORE) ? chunk.get(FIELD_SCORE).asDouble() : null;
            JsonNode chunkData = chunk.get(NODE_CHUNK_DATA);
            String text = chunkData != null && chunkData.has(NODE_TEXT)
                    ? chunkData.get(NODE_TEXT).asString()
                    : null;
            passages.add(new TurSEPassage(id != null ? id.asString() : null, score, text));
        }
        // Adobe documents no ordering guarantee on chunks[], and its own example
        // happens to be descending. Sorting here means the "best passage" a
        // caller grounds on does not depend on that happening to stay true.
        passages.sort(Comparator.comparingDouble(
                passage -> passage.score() != null ? -passage.score() : 0d));
        return passages;
    }

    /**
     * The term-bucket aggregates as facet results, one per requested facet field
     * and in the field order the caller passed — the aggregate {@code id} is the
     * field name, which is what {@code TurContentAiQueryBuilder} set it to.
     */
    public List<TurSEFacetResult> facetResults(JsonNode response,
            List<TurSNSiteFieldExt> facetFields) {
        Map<String, JsonNode> byId = aggregatesById(response);
        List<TurSEFacetResult> facetResults = new ArrayList<>();
        for (TurSNSiteFieldExt field : facetFields) {
            JsonNode aggregate = byId.get(field.getName());
            if (aggregate == null) {
                log.debug("Content AI returned no aggregate for facet '{}'", field.getName());
                continue;
            }
            facetResults.add(toFacetResult(field.getName(),
                    field.getFacetPosition() != null ? field.getFacetPosition() : 0, aggregate));
        }
        return facetResults;
    }

    /** A single named facet, for {@code retrieveFacetResults}. */
    public TurSEFacetResult facetResult(JsonNode response, String facetName) {
        JsonNode aggregate = aggregatesById(response).get(facetName);
        return toFacetResult(facetName, 0, aggregate);
    }

    private Map<String, JsonNode> aggregatesById(JsonNode response) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        if (response == null || !response.has(NODE_AGGREGATES)) {
            return byId;
        }
        for (JsonNode aggregate : response.get(NODE_AGGREGATES)) {
            JsonNode id = aggregate.get("id");
            if (id != null && !id.isNull()) {
                byId.putIfAbsent(id.asString(), aggregate);
            }
        }
        return byId;
    }

    private TurSEFacetResult toFacetResult(String facetName, int position, JsonNode aggregate) {
        TurSEFacetResult facetResult = new TurSEFacetResult();
        facetResult.setFacet(facetName);
        facetResult.setFacetPosition(position);
        JsonNode buckets = bucketArray(aggregate);
        if (buckets == null) {
            return facetResult;
        }
        for (JsonNode bucket : buckets) {
            JsonNode label = bucket.get(FIELD_LABEL);
            if (label == null || label.isNull()) {
                continue;
            }
            String value = label.asString();
            int count = bucket.has(FIELD_COUNT) ? bucket.get(FIELD_COUNT).asInt(0) : 0;
            facetResult.add(value, new TurSEFacetResultAttr(value, count));
        }
        return facetResult;
    }

    private JsonNode bucketArray(JsonNode aggregate) {
        if (aggregate == null) {
            return null;
        }
        JsonNode buckets = aggregate.get(NODE_BUCKETS);
        if (buckets != null && buckets.isArray()) {
            return buckets;
        }
        JsonNode values = aggregate.get(NODE_VALUES);
        return values != null && values.isArray() ? values : null;
    }

    /**
     * Flattens a JSON value into what a Turing field map holds. Arrays become
     * {@code List}s because a multi-valued field is a list everywhere else in
     * Turing; nested objects are kept as their JSON text, which is lossy but
     * honest — the alternative, dropping them, hides the field from the caller
     * entirely.
     */
    private Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(toJavaValue(item)));
            return values;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.toString();
    }
}
