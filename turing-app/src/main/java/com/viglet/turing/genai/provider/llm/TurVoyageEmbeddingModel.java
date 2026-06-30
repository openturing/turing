/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
 * T507 / §XXVIII.3 — a Spring AI {@link EmbeddingModel} backed by the Voyage AI
 * embeddings REST API ({@code voyage-3} / {@code voyage-3-large} family), the
 * retrieval specialist Anthropic itself points customers to (Anthropic has no
 * embedding API of its own).
 *
 * <h2>Asymmetric input types — the differentiator</h2>
 *
 * Voyage takes an {@code input_type} that tunes the vector for how it will be
 * used. Turing applies it asymmetrically via the standard Spring AI VectorStore
 * calling convention, with zero caller changes:
 * <ul>
 *   <li><b>Index time</b> — {@code VectorStore.add(documents)} → {@code document}.</li>
 *   <li><b>Query time</b> — {@code VectorStore.similaritySearch(query)} → {@code query}.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurVoyageEmbeddingModel implements EmbeddingModel {

    static final String DEFAULT_MODEL = "voyage-3";
    /** Native dimensionality of the {@code voyage-3} family. */
    static final int DEFAULT_DIMENSIONS = 1024;
    static final String ENDPOINT = "https://api.voyageai.com/v1/embeddings";
    private static final String INPUT_TYPE_DOCUMENT = "document";
    private static final String INPUT_TYPE_QUERY = "query";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String modelName;
    private final Integer outputDimensionality;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public TurVoyageEmbeddingModel(String apiKey, String modelName, Integer outputDimensionality) {
        this.apiKey = apiKey;
        this.modelName = (modelName == null || modelName.isBlank()) ? DEFAULT_MODEL : modelName.trim();
        this.outputDimensionality = outputDimensionality;
    }

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedTexts(List.of(text), INPUT_TYPE_QUERY);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public float[] embed(Document document) {
        List<float[]> vectors = embedTexts(List.of(getEmbeddingContent(document)), INPUT_TYPE_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
            org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        List<String> contents = documents.stream().map(this::getEmbeddingContent).toList();
        return embedTexts(contents, INPUT_TYPE_DOCUMENT);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedTexts(request.getInstructions(), INPUT_TYPE_QUERY);
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

    private List<float[]> embedTexts(List<String> texts, String inputType) {
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode input = body.putArray("input");
        for (String text : texts) {
            input.add(text == null ? "" : text);
        }
        body.put("model", modelName);
        body.put("input_type", inputType);
        if (outputDimensionality != null && outputDimensionality > 0) {
            body.put("output_dimension", outputDimensionality);
        }
        JsonNode response = post(objectMapper.writeValueAsString(body));
        return toVectors(response);
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
                throw new IllegalStateException("Voyage embeddings returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Voyage embeddings request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voyage embeddings request interrupted", e);
        }
    }

    /** Map the {@code data[].embedding} arrays to float vectors, preserving order. */
    static List<float[]> toVectors(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        List<float[]> vectors = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return vectors;
        }
        for (JsonNode element : data) {
            JsonNode embedding = element.path("embedding");
            float[] vector = new float[embedding.isArray() ? embedding.size() : 0];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embedding.get(i).doubleValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
