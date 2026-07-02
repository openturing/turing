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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.CollectionUtils;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T506 / §XXVIII.2 — a Spring AI {@link EmbeddingModel} over Amazon Bedrock's
 * {@code InvokeModel}, since the {@code spring-ai-bedrock-converse} module is
 * chat-only. Supports the two Bedrock embedding families:
 *
 * <ul>
 *   <li><b>Amazon Titan</b> ({@code amazon.titan-embed-*}) — single-text request
 *       {@code {"inputText": "..."}} → {@code {"embedding": [...]}}.</li>
 *   <li><b>Cohere on Bedrock</b> ({@code cohere.embed-*}) — batch request
 *       {@code {"texts": [...], "input_type": "search_document|search_query"}} →
 *       {@code {"embeddings": [[...]]}}, with the asymmetric document/query input
 *       type applied per the standard Spring AI VectorStore calling convention.</li>
 * </ul>
 *
 * <p>The model id is the Bedrock id from the instance (e.g.
 * {@code amazon.titan-embed-text-v2:0}); the family is detected from its prefix.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurBedrockEmbeddingModel implements EmbeddingModel {

    static final String DEFAULT_MODEL = "amazon.titan-embed-text-v2:0";
    /** Native dimensionality of {@code amazon.titan-embed-text-v2:0}. */
    static final int DEFAULT_DIMENSIONS = 1024;
    private static final String COHERE_INPUT_DOCUMENT = "search_document";
    private static final String COHERE_INPUT_QUERY = "search_query";

    private final BedrockRuntimeClient client;
    private final String modelId;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurBedrockEmbeddingModel(BedrockRuntimeClient client, String modelId) {
        this.client = client;
        this.modelId = (modelId == null || modelId.isBlank()) ? DEFAULT_MODEL : modelId.trim();
    }

    /** Query path — Cohere uses {@code search_query}; Titan is symmetric. */
    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedTexts(List.of(text), COHERE_INPUT_QUERY);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    /** Index path (one document) — Cohere uses {@code search_document}. */
    @Override
    public float[] embed(Document document) {
        List<float[]> vectors = embedTexts(List.of(getEmbeddingContent(document)), COHERE_INPUT_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    /** Index path (batch) — Cohere uses {@code search_document}. */
    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
            org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        List<String> contents = documents.stream().map(this::getEmbeddingContent).toList();
        return embedTexts(contents, COHERE_INPUT_DOCUMENT);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedTexts(request.getInstructions(), COHERE_INPUT_QUERY);
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    private boolean isCohere() {
        return modelId.toLowerCase(Locale.ROOT).startsWith("cohere.");
    }

    private List<float[]> embedTexts(List<String> texts, String cohereInputType) {
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }
        return isCohere() ? embedCohere(texts, cohereInputType) : embedTitan(texts);
    }

    /** Cohere on Bedrock: one batch call for all texts. */
    private List<float[]> embedCohere(List<String> texts, String inputType) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode textsNode = body.putArray("texts");
        for (String text : texts) {
            textsNode.add(text == null ? "" : text);
        }
        body.put("input_type", inputType);
        JsonNode response = invoke(body);
        JsonNode embeddings = response.path("embeddings");
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode vector : embeddings) {
            vectors.add(toFloatArray(vector));
        }
        return vectors;
    }

    /** Titan: one call per text (the API embeds a single {@code inputText}). */
    private List<float[]> embedTitan(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("inputText", text == null ? "" : text);
            JsonNode response = invoke(body);
            vectors.add(toFloatArray(response.path("embedding")));
        }
        return vectors;
    }

    private JsonNode invoke(ObjectNode body) {
        String json = objectMapper.writeValueAsString(body);
        InvokeModelResponse response = client.invokeModel(b -> b
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(json)));
        return objectMapper.readTree(response.body().asUtf8String());
    }

    private static float[] toFloatArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new float[0];
        }
        float[] vector = new float[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            vector[i] = (float) arrayNode.get(i).doubleValue();
        }
        return vector;
    }
}
