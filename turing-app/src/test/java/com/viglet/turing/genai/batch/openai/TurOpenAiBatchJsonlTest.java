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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F.7 / §X.8.a — verifies the OpenAI Batch JSONL wire format both directions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiBatchJsonlTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void buildInputJsonl_emitsOneLinePerRequest_withSystemAndUser() {
        List<TurBatchChatRequest> requests = List.of(
                new TurBatchChatRequest("a", "be terse", "hello", null, 0.2, 256),
                TurBatchChatRequest.of("b", null, "world"));

        String jsonl = TurOpenAiBatchJsonl.buildInputJsonl(requests, "gpt-4o-mini");

        String[] lines = jsonl.strip().split("\n");
        assertThat(lines).hasSize(2);

        JsonNode first = MAPPER.readTree(lines[0]);
        assertThat(first.path("custom_id").asString()).isEqualTo("a");
        assertThat(first.path("method").asString()).isEqualTo("POST");
        assertThat(first.path("url").asString()).isEqualTo(TurOpenAiBatchJsonl.CHAT_COMPLETIONS_URL);
        assertThat(first.path("body").path("model").asString()).isEqualTo("gpt-4o-mini");
        assertThat(first.path("body").path("messages").path(0).path("role").asString())
                .isEqualTo("system");
        assertThat(first.path("body").path("messages").path(1).path("content").asString())
                .isEqualTo("hello");
        assertThat(first.path("body").path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(first.path("body").path("max_tokens").asInt()).isEqualTo(256);

        JsonNode second = MAPPER.readTree(lines[1]);
        // no system prompt → only the user message
        assertThat(second.path("body").path("messages").size()).isEqualTo(1);
        assertThat(second.path("body").path("messages").path(0).path("role").asString())
                .isEqualTo("user");
    }

    @Test
    void buildInputJsonl_perRequestModelOverridesDefault() {
        String jsonl = TurOpenAiBatchJsonl.buildInputJsonl(
                List.of(new TurBatchChatRequest("a", null, "hi", "gpt-4o", null, null)),
                "gpt-4o-mini");
        JsonNode node = MAPPER.readTree(jsonl.strip());
        assertThat(node.path("body").path("model").asString()).isEqualTo("gpt-4o");
    }

    @Test
    void parseOutputJsonl_extractsContentAndUsage() {
        String output = """
                {"custom_id":"a","response":{"status_code":200,"body":{"choices":[{"message":{"content":"hi there"}}],"usage":{"prompt_tokens":10,"completion_tokens":5}}},"error":null}
                """;
        List<TurBatchChatResult> results = TurOpenAiBatchJsonl.parseOutputJsonl(output);
        assertThat(results).hasSize(1);
        TurBatchChatResult result = results.get(0);
        assertThat(result.customId()).isEqualTo("a");
        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("hi there");
        assertThat(result.inputTokens()).isEqualTo(10L);
        assertThat(result.outputTokens()).isEqualTo(5L);
    }

    @Test
    void parseOutputJsonl_topLevelError_becomesFailedResult() {
        String output =
                "{\"custom_id\":\"a\",\"response\":null,\"error\":{\"message\":\"bad request\"}}";
        List<TurBatchChatResult> results = TurOpenAiBatchJsonl.parseOutputJsonl(output);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).isEqualTo("bad request");
    }

    @Test
    void parseOutputJsonl_non2xxStatus_becomesFailedResult() {
        String output =
                "{\"custom_id\":\"a\",\"response\":{\"status_code\":429,\"body\":{\"error\":{\"message\":\"rate limited\"}}},\"error\":null}";
        List<TurBatchChatResult> results = TurOpenAiBatchJsonl.parseOutputJsonl(output);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).error()).isEqualTo("rate limited");
    }

    @Test
    void parseOutputJsonl_blankInput_isEmpty() {
        assertThat(TurOpenAiBatchJsonl.parseOutputJsonl("")).isEmpty();
        assertThat(TurOpenAiBatchJsonl.parseOutputJsonl(null)).isEmpty();
        assertThat(TurOpenAiBatchJsonl.parseOutputJsonl("\n  \n")).isEmpty();
    }
}
