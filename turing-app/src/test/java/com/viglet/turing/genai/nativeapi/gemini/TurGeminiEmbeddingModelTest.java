/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentResponse;

/**
 * T495 / §X.19 — unit coverage for the Gemini embedding model's pure pieces
 * (dimensionality resolution + SDK response → float[] mapping). The
 * {@code embedContent} call itself needs a live client and is exercised by the
 * real-LLM-gated path.
 */
class TurGeminiEmbeddingModelTest {

    @Test
    void dimensionsDefaultsToNativeWhenNoOverride() {
        TurGeminiEmbeddingModel model = new TurGeminiEmbeddingModel(null, null, null);
        assertThat(model.dimensions()).isEqualTo(TurGeminiEmbeddingModel.DEFAULT_DIMENSIONS);
    }

    @Test
    void dimensionsUsesMatryoshkaOverride() {
        TurGeminiEmbeddingModel model = new TurGeminiEmbeddingModel(null, "gemini-embedding-001", 768);
        assertThat(model.dimensions()).isEqualTo(768);
    }

    @Test
    void dimensionsIgnoresNonPositiveOverride() {
        TurGeminiEmbeddingModel model = new TurGeminiEmbeddingModel(null, null, 0);
        assertThat(model.dimensions()).isEqualTo(TurGeminiEmbeddingModel.DEFAULT_DIMENSIONS);
    }

    @Test
    void toVectorsMapsContentEmbeddings() {
        EmbedContentResponse response = EmbedContentResponse.builder()
                .embeddings(List.of(
                        ContentEmbedding.builder().values(List.of(0.1f, 0.2f, 0.3f)).build(),
                        ContentEmbedding.builder().values(List.of(0.4f, 0.5f)).build()))
                .build();

        List<float[]> vectors = TurGeminiEmbeddingModel.toVectors(response);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f);
    }

    @Test
    void toVectorsHandlesEmptyResponse() {
        assertThat(TurGeminiEmbeddingModel.toVectors(null)).isEmpty();
        assertThat(TurGeminiEmbeddingModel.toVectors(EmbedContentResponse.builder().build())).isEmpty();
    }
}
