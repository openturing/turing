/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.CollectionUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T512 / §XXVIII.8 — a Spring AI {@link EmbeddingModel} backed by the Voyage AI
 * <b>contextualized</b> embeddings REST API ({@code voyage-context-3}), which
 * embeds each chunk with awareness of the other chunks of the same document.
 *
 * <p>The endpoint takes {@code inputs} as a list of <i>documents</i>, each a
 * list of <i>chunks</i>, and returns a parallel {@code data[].data[].embedding}
 * structure (per-document, per-chunk). {@link #embedDocumentChunks(List)} sends
 * all chunks of one document at once — the context-aware index path. Single
 * {@link #embed(String)} / {@link #embed(Document)} calls degrade gracefully to
 * a one-document, one-chunk request (no surrounding context to read), with the
 * asymmetric {@code query}/{@code document} input type the rest of the Voyage
 * family uses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurVoyageContextualEmbeddingModel implements EmbeddingModel, TurContextualEmbeddingModel {

    static final String DEFAULT_MODEL = "voyage-context-3";
    /** Native dimensionality of the {@code voyage-context-3} model. */
    static final int DEFAULT_DIMENSIONS = 1024;
    static final String ENDPOINT = "https://api.voyageai.com/v1/contextualizedembeddings";
    private static final String INPUT_TYPE_DOCUMENT = "document";
    private static final String INPUT_TYPE_QUERY = "query";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final String apiKey;
    private final String modelName;
    private final Integer outputDimensionality;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public TurVoyageContextualEmbeddingModel(String apiKey, String modelName, Integer outputDimensionality) {
        this.apiKey = apiKey;
        this.modelName = (modelName == null || modelName.isBlank()) ? DEFAULT_MODEL : modelName.trim();
        this.outputDimensionality = outputDimensionality;
    }

    // --- contextual (index) path -------------------------------------------

    @Override
    public List<float[]> embedDocumentChunks(List<String> chunks) {
        if (CollectionUtils.isEmpty(chunks)) {
            return List.of();
        }
        JsonNode response = post(buildBody(List.of(chunks), INPUT_TYPE_DOCUMENT));
        List<List<float[]>> docs = toDocumentVectors(response);
        return docs.isEmpty() ? List.of() : docs.get(0);
    }

    // --- Spring AI EmbeddingModel (query + single-chunk) -------------------

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedSingles(List.of(text == null ? "" : text), INPUT_TYPE_QUERY);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public float[] embed(Document document) {
        List<float[]> vectors = embedSingles(List.of(getEmbeddingContent(document)), INPUT_TYPE_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
            org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        List<String> contents = documents.stream().map(this::getEmbeddingContent).toList();
        return embedSingles(contents, INPUT_TYPE_DOCUMENT);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedSingles(request.getInstructions(), INPUT_TYPE_QUERY);
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return outputDimensionality != null && outputDimensionality > 0
                ? outputDimensionality
                : DEFAULT_DIMENSIONS;
    }

    /**
     * Embeds N independent texts as N single-chunk documents — used by the query
     * and single-chunk paths where there is no sibling context. Each text becomes
     * its own {@code inputs} entry so the result is one vector per text.
     */
    private List<float[]> embedSingles(List<String> texts, String inputType) {
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }
        List<List<String>> documentsAsSingles = texts.stream()
                .map(t -> List.of(t == null ? "" : t))
                .toList();
        JsonNode response = post(buildBody(documentsAsSingles, inputType));
        List<List<float[]>> docs = toDocumentVectors(response);
        List<float[]> flat = new ArrayList<>(docs.size());
        for (List<float[]> doc : docs) {
            flat.add(doc.isEmpty() ? new float[0] : doc.get(0));
        }
        return flat;
    }

    private String buildBody(List<List<String>> documents, String inputType) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode inputs = body.putArray("inputs");
        for (List<String> chunks : documents) {
            ArrayNode doc = inputs.addArray();
            for (String chunk : chunks) {
                doc.add(chunk == null ? "" : chunk);
            }
        }
        body.put("model", modelName);
        body.put("input_type", inputType);
        if (outputDimensionality != null && outputDimensionality > 0) {
            body.put("output_dimension", outputDimensionality);
        }
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode post(String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Voyage contextual embeddings returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Voyage contextual embeddings request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voyage contextual embeddings request interrupted", e);
        }
    }

    /**
     * Maps the nested {@code data[].data[].embedding} response into a list of
     * documents, each a list of per-chunk float vectors (order preserved).
     */
    static List<List<float[]>> toDocumentVectors(JsonNode response) {
        List<List<float[]>> documents = new ArrayList<>();
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) {
            return documents;
        }
        for (JsonNode docNode : data) {
            JsonNode chunkData = docNode.path("data");
            List<float[]> chunkVectors = new ArrayList<>();
            if (chunkData.isArray()) {
                for (JsonNode chunkNode : chunkData) {
                    chunkVectors.add(toVector(chunkNode.path("embedding")));
                }
            }
            documents.add(chunkVectors);
        }
        return documents;
    }

    private static float[] toVector(JsonNode embedding) {
        float[] vector = new float[embedding.isArray() ? embedding.size() : 0];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) embedding.get(i).doubleValue();
        }
        return vector;
    }
}
