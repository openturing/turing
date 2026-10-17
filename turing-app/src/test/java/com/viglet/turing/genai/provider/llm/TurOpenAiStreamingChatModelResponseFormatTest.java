/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import tools.jackson.databind.JsonNode;

/**
 * T1090 — the hand-rolled streaming path sends {@code response_format} in the
 * same shape {@code OpenAiChatModel} sends on the blocking path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiStreamingChatModelResponseFormatTest {

    @Test
    void plainTextOrAbsentSendsNothing() {
        assertThat(TurOpenAiStreamingChatModel.responseFormatBody(null)).isEmpty();
        assertThat(TurOpenAiStreamingChatModel.responseFormatBody(OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.TEXT).build())).isEmpty();
    }

    @Test
    void jsonObjectIsSentAsItsType() {
        assertThat(TurOpenAiStreamingChatModel.responseFormatBody(OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT).build()))
                .isEqualTo(Map.of("type", "json_object"));
    }

    @Test
    void jsonSchemaIsSentWithTheParsedSchemaAndStrict() {
        Map<String, Object> body = TurOpenAiStreamingChatModel.responseFormatBody(
                OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema("{\"type\":\"object\"}")
                        .strict(true)
                        .build());

        assertThat(body).containsEntry("type", "json_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonSchema = (Map<String, Object>) body.get("json_schema");
        assertThat(jsonSchema).containsEntry("name", "json_schema").containsEntry("strict", true);
        assertThat(((JsonNode) jsonSchema.get("schema")).get("type").asString()).isEqualTo("object");
    }

    @Test
    void stopSequencesFromThePromptReachTheBody() {
        TurOpenAiStreamingChatModel model = new TurOpenAiStreamingChatModel("http://localhost", "k", "gpt",
                OpenAiChatOptions.builder().model("gpt").build());

        Map<String, Object> body = model.buildRequestBody(new Prompt(List.of(new UserMessage("hi")),
                OpenAiChatOptions.builder().stop(List.of("END")).build()));

        assertThat(body).containsEntry("stop", List.of("END"));
    }

    @Test
    void noStopSequencesSendsNoStopField() {
        TurOpenAiStreamingChatModel model = new TurOpenAiStreamingChatModel("http://localhost", "k", "gpt",
                OpenAiChatOptions.builder().model("gpt").build());

        assertThat(model.buildRequestBody(new Prompt(List.of(new UserMessage("hi"))))).doesNotContainKey("stop");
    }
}
