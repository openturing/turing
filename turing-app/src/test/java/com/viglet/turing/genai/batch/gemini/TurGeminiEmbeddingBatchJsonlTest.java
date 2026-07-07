/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.batch.TurBatchEmbeddingRequest;
import com.viglet.turing.genai.batch.TurBatchEmbeddingResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T496 / §X.19 — unit coverage for the Gemini embeddings batch JSONL contract
 * (keyed input + lenient output parsing). The Files/Batch SDK glue is
 * integration-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGeminiEmbeddingBatchJsonlTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void buildsKeyedEmbedContentLinePerRequest() {
        String jsonl = TurGeminiEmbeddingBatchJsonl.buildInputJsonl(
                List.of(new TurBatchEmbeddingRequest("chunk-1", "hello world", "gemini-embedding-001")));

        JsonNode line = MAPPER.readTree(jsonl.trim());
        assertThat(line.path("key").asString()).isEqualTo("chunk-1");
        JsonNode request = line.path("request");
        assertThat(request.path("content").path("parts").path(0).path("text").asString())
                .isEqualTo("hello world");
        // Re-embedding is index-side, so the task type matches the synchronous document path.
        assertThat(request.path("taskType").asString()).isEqualTo("RETRIEVAL_DOCUMENT");
    }

    @Test
    void nullTextSerializesAsEmptyString() {
        String jsonl = TurGeminiEmbeddingBatchJsonl.buildInputJsonl(
                List.of(new TurBatchEmbeddingRequest("k", null, null)));
        JsonNode request = MAPPER.readTree(jsonl.trim()).path("request");
        assertThat(request.path("content").path("parts").path(0).path("text").asString()).isEmpty();
    }

    @Test
    void roundTripsCustomIdAcrossBuildAndParse() {
        // The key is what carries Turing's chunk id (customId) through the batch.
        String input = TurGeminiEmbeddingBatchJsonl.buildInputJsonl(
                List.of(new TurBatchEmbeddingRequest("doc-42", "q", null)));
        String key = MAPPER.readTree(input.trim()).path("key").asString();
        assertThat(key).isEqualTo("doc-42");
    }

    @Test
    void parsesSingleEmbeddingShape() {
        String out = "{\"key\":\"chunk-1\",\"response\":{\"embedding\":{\"values\":[0.1,0.2,0.3]}}}";
        List<TurBatchEmbeddingResult> results = TurGeminiEmbeddingBatchJsonl.parseOutputJsonl(out);

        assertThat(results).hasSize(1);
        TurBatchEmbeddingResult r = results.get(0);
        assertThat(r.success()).isTrue();
        assertThat(r.customId()).isEqualTo("chunk-1");
        assertThat(r.embedding()).hasSize(3);
        assertThat(r.embedding()[0]).isEqualTo(0.1f, within(1e-6f));
        assertThat(r.embedding()[2]).isEqualTo(0.3f, within(1e-6f));
    }

    @Test
    void parsesListEmbeddingShape() {
        // Tolerate the embeddings[] list shape too.
        String out = "{\"key\":\"k\",\"response\":{\"embeddings\":[{\"values\":[0.5,0.6]}]}}";
        List<TurBatchEmbeddingResult> results = TurGeminiEmbeddingBatchJsonl.parseOutputJsonl(out);
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).embedding()).hasSize(2);
        assertThat(results.get(0).embedding()[1]).isEqualTo(0.6f, within(1e-6f));
    }

    @Test
    void errorOutputLineBecomesFailed() {
        String out = "{\"key\":\"chunk-2\",\"error\":{\"message\":\"quota exceeded\"}}";
        List<TurBatchEmbeddingResult> results = TurGeminiEmbeddingBatchJsonl.parseOutputJsonl(out);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).customId()).isEqualTo("chunk-2");
        assertThat(results.get(0).error()).contains("quota exceeded");
    }

    @Test
    void missingResponseBecomesFailed() {
        List<TurBatchEmbeddingResult> results = TurGeminiEmbeddingBatchJsonl.parseOutputJsonl("{\"key\":\"k\"}");
        assertThat(results.get(0).success()).isFalse();
    }

    @Test
    void emptyValuesBecomeFailed() {
        String out = "{\"key\":\"k\",\"response\":{\"embedding\":{\"values\":[]}}}";
        List<TurBatchEmbeddingResult> results = TurGeminiEmbeddingBatchJsonl.parseOutputJsonl(out);
        assertThat(results.get(0).success()).isFalse();
    }

    @Test
    void blankOutputYieldsNoResults() {
        assertThat(TurGeminiEmbeddingBatchJsonl.parseOutputJsonl("")).isEmpty();
        assertThat(TurGeminiEmbeddingBatchJsonl.parseOutputJsonl("  \n  ")).isEmpty();
    }
}
