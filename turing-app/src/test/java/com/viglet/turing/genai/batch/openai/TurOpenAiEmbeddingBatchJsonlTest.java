/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.batch.TurBatchEmbeddingRequest;
import com.viglet.turing.genai.batch.TurBatchEmbeddingResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F.7 / §X.8.c — verifies the OpenAI {@code /v1/embeddings} Batch JSONL contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiEmbeddingBatchJsonlTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void buildInputJsonl_emitsEmbeddingsRequestLine() {
        String jsonl = TurOpenAiEmbeddingBatchJsonl.buildInputJsonl(
                List.of(new TurBatchEmbeddingRequest("c1", "hello world", null)),
                "text-embedding-3-small");
        JsonNode node = MAPPER.readTree(jsonl.strip());
        assertThat(node.path("custom_id").asString()).isEqualTo("c1");
        assertThat(node.path("url").asString()).isEqualTo(TurOpenAiEmbeddingBatchJsonl.EMBEDDINGS_URL);
        assertThat(node.path("body").path("model").asString()).isEqualTo("text-embedding-3-small");
        assertThat(node.path("body").path("input").asString()).isEqualTo("hello world");
    }

    @Test
    void buildInputJsonl_perRequestModelOverridesDefault() {
        String jsonl = TurOpenAiEmbeddingBatchJsonl.buildInputJsonl(
                List.of(new TurBatchEmbeddingRequest("c1", "x", "text-embedding-3-large")),
                "text-embedding-3-small");
        JsonNode node = MAPPER.readTree(jsonl.strip());
        assertThat(node.path("body").path("model").asString()).isEqualTo("text-embedding-3-large");
    }

    @Test
    void parseOutputJsonl_extractsVector() {
        String output = "{\"custom_id\":\"c1\",\"response\":{\"status_code\":200,"
                + "\"body\":{\"data\":[{\"embedding\":[0.1,0.2,0.3],\"index\":0}],"
                + "\"usage\":{\"prompt_tokens\":3}}},\"error\":null}";
        List<TurBatchEmbeddingResult> results = TurOpenAiEmbeddingBatchJsonl.parseOutputJsonl(output);
        assertThat(results).hasSize(1);
        TurBatchEmbeddingResult result = results.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.customId()).isEqualTo("c1");
        assertThat(result.embedding()).hasSize(3);
        assertThat(result.embedding()[0]).isEqualTo(0.1f, within(1e-6f));
        assertThat(result.embedding()[2]).isEqualTo(0.3f, within(1e-6f));
    }

    @Test
    void parseOutputJsonl_error_becomesFailed() {
        String output = "{\"custom_id\":\"c1\",\"response\":null,\"error\":{\"message\":\"boom\"}}";
        List<TurBatchEmbeddingResult> results = TurOpenAiEmbeddingBatchJsonl.parseOutputJsonl(output);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).isEqualTo("boom");
    }

    @Test
    void parseOutputJsonl_missingEmbedding_becomesFailed() {
        String output = "{\"custom_id\":\"c1\",\"response\":{\"status_code\":200,"
                + "\"body\":{\"data\":[]}},\"error\":null}";
        List<TurBatchEmbeddingResult> results = TurOpenAiEmbeddingBatchJsonl.parseOutputJsonl(output);
        assertThat(results.get(0).success()).isFalse();
    }
}
