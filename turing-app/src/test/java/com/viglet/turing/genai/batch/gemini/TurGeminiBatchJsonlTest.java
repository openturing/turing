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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T496 / §X.19 — unit coverage for the Gemini batch JSONL contract (keyed input
 * + output parsing). The Files/Batch SDK glue is integration-only.
 */
class TurGeminiBatchJsonlTest {

    @Test
    void buildsKeyedInputLinePerRequest() {
        String jsonl = TurGeminiBatchJsonl.buildInputJsonl(List.of(
                new TurBatchChatRequest("site-1", "You are concise.", "Summarize", null, 0.2, 256)));

        JsonNode line = JsonMapper.builder().build().readTree(jsonl.trim());
        assertThat(line.path("key").asString()).isEqualTo("site-1");
        JsonNode request = line.path("request");
        assertThat(request.path("contents").path(0).path("role").asString()).isEqualTo("user");
        assertThat(request.path("contents").path(0).path("parts").path(0).path("text").asString())
                .isEqualTo("Summarize");
        assertThat(request.path("systemInstruction").path("parts").path(0).path("text").asString())
                .isEqualTo("You are concise.");
        assertThat(request.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(256);
    }

    @Test
    void omitsSystemInstructionAndConfigWhenAbsent() {
        String jsonl = TurGeminiBatchJsonl.buildInputJsonl(List.of(
                new TurBatchChatRequest("k", null, "hi", null, null, null)));
        JsonNode request = JsonMapper.builder().build().readTree(jsonl.trim()).path("request");
        assertThat(request.has("systemInstruction")).isFalse();
        assertThat(request.has("generationConfig")).isFalse();
    }

    @Test
    void parsesSuccessfulOutputLine() {
        String out = "{\"key\":\"site-1\",\"response\":{\"candidates\":[{\"content\":"
                + "{\"parts\":[{\"text\":\"A summary.\"}]}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":4}}}";
        List<TurBatchChatResult> results = TurGeminiBatchJsonl.parseOutputJsonl(out);

        assertThat(results).hasSize(1);
        TurBatchChatResult r = results.get(0);
        assertThat(r.customId()).isEqualTo("site-1");
        assertThat(r.success()).isTrue();
        assertThat(r.content()).isEqualTo("A summary.");
        assertThat(r.inputTokens()).isEqualTo(10L);
        assertThat(r.outputTokens()).isEqualTo(4L);
    }

    @Test
    void parsesErrorOutputLine() {
        String out = "{\"key\":\"site-2\",\"error\":{\"message\":\"quota exceeded\"}}";
        List<TurBatchChatResult> results = TurGeminiBatchJsonl.parseOutputJsonl(out);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).customId()).isEqualTo("site-2");
        assertThat(results.get(0).error()).contains("quota exceeded");
    }

    @Test
    void concatenatesMultipleTextParts() {
        String out = "{\"key\":\"k\",\"response\":{\"candidates\":[{\"content\":{\"parts\":"
                + "[{\"text\":\"Hello \"},{\"text\":\"world\"}]}}]}}";
        List<TurBatchChatResult> results = TurGeminiBatchJsonl.parseOutputJsonl(out);
        assertThat(results.get(0).content()).isEqualTo("Hello world");
    }

    @Test
    void missingResponseBecomesFailure() {
        String out = "{\"key\":\"k\"}";
        List<TurBatchChatResult> results = TurGeminiBatchJsonl.parseOutputJsonl(out);
        assertThat(results.get(0).success()).isFalse();
    }

    @Test
    void blankOutputYieldsNoResults() {
        assertThat(TurGeminiBatchJsonl.parseOutputJsonl("")).isEmpty();
        assertThat(TurGeminiBatchJsonl.parseOutputJsonl("  \n  ")).isEmpty();
    }

    @Test
    void roundTripsCustomIdAcrossBuildAndParse() {
        // The key is what carries Turing's customId through the batch.
        String input = TurGeminiBatchJsonl.buildInputJsonl(List.of(
                new TurBatchChatRequest("doc-42", "sys", "q", null, null, null)));
        String key = JsonMapper.builder().build().readTree(input.trim()).path("key").asString();
        assertThat(key).isEqualTo("doc-42");
    }
}
