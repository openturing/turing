/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.CollectionUtils;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

/**
 * T495 / §X.19 — a Spring AI {@link EmbeddingModel} backed by the Google GenAI
 * SDK's {@code embedContent} ({@code gemini-embedding-001}), replacing the
 * former {@code UnsupportedOperationException} on the native Gemini provider.
 *
 * <h2>Asymmetric task types — the differentiator</h2>
 *
 * Gemini's embedding endpoint takes a {@code taskType} that tunes the vector for
 * how it will be used. Turing exploits the standard Spring AI VectorStore
 * calling convention to apply it asymmetrically with zero caller changes:
 * <ul>
 *   <li><b>Index time</b> — {@code VectorStore.add(documents)} reaches
 *       {@link #embed(Document)} / {@link #embed(List, EmbeddingOptions,
 *       org.springframework.ai.embedding.BatchingStrategy)}, embedded as
 *       {@code RETRIEVAL_DOCUMENT}.</li>
 *   <li><b>Query time</b> — {@code VectorStore.similaritySearch(query)} reaches
 *       {@link #embed(String)} / {@link #call(EmbeddingRequest)}, embedded as
 *       {@code RETRIEVAL_QUERY}.</li>
 * </ul>
 * Embedding the query and the document with matched-but-distinct task types is a
 * measured retrieval-quality win that the OpenAI/Anthropic providers can't match
 * (Anthropic has no embeddings at all).
 *
 * <p>{@code outputDimensionality} (Matryoshka truncation) is applied when
 * configured, letting an instance trade a smaller vector for lower storage/IO.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurGeminiEmbeddingModel implements EmbeddingModel {

    static final String DEFAULT_MODEL = "gemini-embedding-001";
    /** Native (un-truncated) dimensionality of {@code gemini-embedding-001}. */
    static final int DEFAULT_DIMENSIONS = 3072;
    static final String TASK_TYPE_DOCUMENT = "RETRIEVAL_DOCUMENT";
    static final String TASK_TYPE_QUERY = "RETRIEVAL_QUERY";

    private final Client client;
    private final String modelName;
    private final Integer outputDimensionality;

    public TurGeminiEmbeddingModel(Client client, String modelName, Integer outputDimensionality) {
        this.client = client;
        this.modelName = modelName == null || modelName.isBlank() ? DEFAULT_MODEL : modelName;
        this.outputDimensionality = outputDimensionality;
    }

    /** Query path (single string) — embedded as {@code RETRIEVAL_QUERY}. */
    @Override
    public float[] embed(String text) {
        List<float[]> vectors = embedTexts(List.of(text), TASK_TYPE_QUERY);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    /** Index path (one document) — embedded as {@code RETRIEVAL_DOCUMENT}. */
    @Override
    public float[] embed(Document document) {
        List<float[]> vectors = embedTexts(List.of(getEmbeddingContent(document)), TASK_TYPE_DOCUMENT);
        return vectors.isEmpty() ? new float[0] : vectors.get(0);
    }

    /** Index path (batch of documents) — embedded as {@code RETRIEVAL_DOCUMENT}. */
    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options,
            org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
        List<String> contents = documents.stream().map(this::getEmbeddingContent).toList();
        return embedTexts(contents, TASK_TYPE_DOCUMENT);
    }

    /**
     * The core request path. Spring AI routes {@code embed(String)} /
     * {@code embed(List&lt;String&gt;)} and the {@code VectorStore} query here, so
     * it defaults to {@code RETRIEVAL_QUERY}.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<float[]> vectors = embedTexts(texts, TASK_TYPE_QUERY);
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

    /** Embed a batch of texts with the given task type, in one {@code embedContent} call. */
    private List<float[]> embedTexts(List<String> texts, String taskType) {
        if (CollectionUtils.isEmpty(texts)) {
            return List.of();
        }
        EmbedContentConfig.Builder config = EmbedContentConfig.builder().taskType(taskType);
        if (outputDimensionality != null && outputDimensionality > 0) {
            config.outputDimensionality(outputDimensionality);
        }
        EmbedContentResponse response = client.models.embedContent(modelName, texts, config.build());
        return toVectors(response);
    }

    /** Map the SDK response's {@link ContentEmbedding} list to float arrays. */
    static List<float[]> toVectors(EmbedContentResponse response) {
        List<ContentEmbedding> embeddings = response == null
                ? List.of()
                : response.embeddings().orElse(List.of());
        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (ContentEmbedding embedding : embeddings) {
            List<Float> values = embedding.values().orElse(List.of());
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i);
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
