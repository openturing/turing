/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T338 / §XVII.2 — shared HTTP transport for the cross-encoder and Cohere
 * rerank strategies. Both speak the de-facto {@code /rerank} contract — {@code
 * POST {query, documents[], model, top_n}} returning per-document relevance
 * scores — so the wire handling lives here once.
 *
 * <p>Returns a ranking as an ordered list of <b>0-based candidate indices</b>,
 * most relevant first (mirroring the LLM strategy's parse output), so callers
 * map indices back to {@code Document}s without this class knowing about Spring
 * AI types.
 *
 * <p>Tolerant of the two response shapes seen in the wild:
 * <ul>
 *   <li><b>Bare array</b> (HuggingFace TEI): {@code [{"index":0,"score":0.9}, …]}</li>
 *   <li><b>{@code {results:[…]}} envelope</b> (Cohere, Jina): each element has
 *       {@code index} + {@code relevance_score} (or {@code score}).</li>
 * </ul>
 * The list is assumed pre-sorted by the server; we preserve its order, only
 * de-duping and bounds-checking. Short timeout, no retry — the
 * {@link com.viglet.turing.genai.rag.TurRagReranker} facade's fail-open is the
 * safety net.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurHttpRerankClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * Calls a {@code /rerank} endpoint and returns the server's ranking as
     * 0-based indices into {@code documents}, most relevant first.
     *
     * @param endpoint    full {@code /rerank} URL (non-blank)
     * @param apiKey      bearer token, or {@code null}/blank for no auth
     * @param model       model name to request, or {@code null}/blank to omit
     * @param query       the user query
     * @param documents   candidate texts, in candidate order
     * @param topN        how many results to ask the server for
     * @throws RuntimeException on any transport/HTTP/parse failure — the caller
     *                          (facade) maps this to "keep retrieval order"
     */
    public List<Integer> rankIndices(String endpoint, String apiKey, String model,
            String query, List<String> documents, int topN) {
        return rankIndices(endpoint, apiKey, model, query, documents, topN, "top_n");
    }

    /**
     * Variant that lets the caller pick the result-count parameter name, since
     * the de-facto contract is not fully uniform: Cohere/TEI use {@code top_n}
     * while Voyage AI uses {@code top_k} (T507). The response side is already
     * tolerant of the bare-array, {@code results} and {@code data} shapes.
     *
     * @param topParamName the JSON field name for the result count
     *                     (e.g. {@code "top_n"} or {@code "top_k"})
     */
    public List<Integer> rankIndices(String endpoint, String apiKey, String model,
            String query, List<String> documents, int topN, String topParamName) {
        String body = buildRequestBody(model, query, documents, topN, topParamName);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("rerank endpoint returned HTTP "
                        + response.statusCode());
            }
            return parseIndices(response.body(), documents.size());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("rerank request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rerank request interrupted", e);
        }
    }

    private String buildRequestBody(String model, String query, List<String> documents, int topN,
            String topParamName) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("query", query);
        if (model != null && !model.isBlank()) {
            root.put("model", model.trim());
        }
        root.put((topParamName == null || topParamName.isBlank()) ? "top_n" : topParamName, topN);
        ArrayNode docs = root.putArray("documents");
        for (String doc : documents) {
            docs.add(doc == null ? "" : doc);
        }
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parses either a bare array or a {@code {results:[…]}} envelope into the
     * ordered, deduped, bounds-checked 0-based index list.
     */
    private List<Integer> parseIndices(String responseBody, int count) {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode list = root;
        if (!root.isArray()) {
            // Cohere/Jina use {results:[…]}; Voyage AI uses {data:[…]}.
            list = root.has("results") ? root.path("results") : root.path("data");
        }
        if (!list.isArray()) {
            throw new IllegalStateException(
                    "rerank response has neither a top-level array nor 'results'/'data'");
        }
        Set<Integer> ordered = new LinkedHashSet<>();
        for (JsonNode element : list) {
            JsonNode indexNode = element.path("index");
            if (!indexNode.isNumber()) {
                continue;
            }
            int idx = indexNode.intValue();
            if (idx >= 0 && idx < count) {
                ordered.add(idx);
            }
        }
        return new ArrayList<>(ordered);
    }
}
