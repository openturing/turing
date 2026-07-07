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
import java.util.Base64;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T511 / §XXVIII.7 — a Spring AI {@link EmbeddingModel} backed by the Voyage AI
 * <b>multimodal</b> embeddings REST API ({@code voyage-multimodal-3}), which
 * embeds text and images into one shared vector space. This is the model behind
 * the "search text → match an image" retrieval surface: PDF page-images,
 * diagrams and screenshots embedded with {@link #embedImage} land next to a
 * plain-text query embedded with {@link #embed(String)} in the same KNN index.
 *
 * <p>Text follows the same asymmetric {@code input_type} convention as
 * {@link TurVoyageEmbeddingModel} ({@code query} at search time, {@code document}
 * at index time). Images are always embedded as {@code document}.
 *
 * <p>The multimodal endpoint takes a list of {@code inputs}, each a {@code content}
 * array of typed parts ({@code text} / {@code image_base64}); the response shape
 * ({@code data[].embedding}) matches the text endpoint, so vector decoding is
 * shared with {@link TurVoyageEmbeddingModel#toVectors(JsonNode)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurVoyageMultimodalEmbeddingModel implements EmbeddingModel, TurMultimodalEmbeddingModel {

    static final String DEFAULT_MODEL = "voyage-multimodal-3";
    /** Native dimensionality of the {@code voyage-multimodal-3} model. */
    static final int DEFAULT_DIMENSIONS = 1024;
    static final String ENDPOINT = "https://api.voyageai.com/v1/multimodalembeddings";
    private static final String INPUT_TYPE_DOCUMENT = "document";
    private static final String INPUT_TYPE_QUERY = "query";
    private static final String DEFAULT_IMAGE_MIME = "image/png";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private final String apiKey;
    private final String modelName;
    private final Integer outputDimensionality;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public TurVoyageMultimodalEmbeddingModel(String apiKey, String modelName, Integer outputDimensionality) {
        this.apiKey = apiKey;
        this.modelName = (modelName == null || modelName.isBlank()) ? DEFAULT_MODEL : modelName.trim();
        this.outputDimensionality = outputDimensionality;
    }

    // --- text side (shared vector space) -----------------------------------

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedInputs(List.of(textInput(text)), INPUT_TYPE_QUERY);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public float[] embed(Document document) {
        List<float[]> vectors = embedInputs(List.of(textInput(getEmbeddingContent(document))), INPUT_TYPE_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
            org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        List<ObjectNode> inputs = documents.stream()
                .map(doc -> textInput(getEmbeddingContent(doc)))
                .toList();
        return embedInputs(inputs, INPUT_TYPE_DOCUMENT);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<ObjectNode> inputs = request.getInstructions().stream()
                .map(this::textInput)
                .toList();
        List<float[]> vectors = embedInputs(inputs, INPUT_TYPE_QUERY);
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

    // --- image side --------------------------------------------------------

    @Override
    public float[] embedImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new float[0];
        }
        List<float[]> vectors = embedInputs(List.of(imageInput(imageBytes, mimeType)), INPUT_TYPE_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    // --- request building --------------------------------------------------

    private ObjectNode textInput(String text) {
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode content = input.putArray("content");
        ObjectNode part = content.addObject();
        part.put("type", "text");
        part.put("text", text == null ? "" : text);
        return input;
    }

    private ObjectNode imageInput(byte[] imageBytes, String mimeType) {
        String mime = StringUtils.hasText(mimeType) ? mimeType.trim() : DEFAULT_IMAGE_MIME;
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode content = input.putArray("content");
        ObjectNode part = content.addObject();
        part.put("type", "image_base64");
        part.put("image_base64", dataUri);
        return input;
    }

    private List<float[]> embedInputs(List<ObjectNode> inputs, String inputType) {
        if (CollectionUtils.isEmpty(inputs)) {
            return List.of();
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode inputsArray = body.putArray("inputs");
        inputs.forEach(inputsArray::add);
        body.put("model", modelName);
        body.put("input_type", inputType);
        if (outputDimensionality != null && outputDimensionality > 0) {
            body.put("output_dimension", outputDimensionality);
        }
        JsonNode response = post(objectMapper.writeValueAsString(body));
        return TurVoyageEmbeddingModel.toVectors(response);
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
                throw new IllegalStateException("Voyage multimodal embeddings returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Voyage multimodal embeddings request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voyage multimodal embeddings request interrupted", e);
        }
    }
}
