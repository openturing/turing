/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * T1102 — the overrides shared by the gateway and the native chat endpoint,
 * applied to base options directly (the tools path's options included).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPromptOverridesTest {

    private static final String PARAM = "responseFormat";

    private static TurPromptOverrides format(String type, Map<String, Object> schema, Boolean strict) {
        return new TurPromptOverrides(null, null, null, null, type, schema, strict);
    }

    @Test
    void nothingSetReturnsTheBaseOptionsThemselves() {
        ChatOptions base = OpenAiChatOptions.builder().model("gpt").build();
        TurPromptOverrides none = new TurPromptOverrides(null, null, null, List.of(), "text", null, null);

        assertThat(none.isEmpty()).isTrue();
        assertThat(none.applyTo(base, "gpt", PARAM)).isSameAs(base);
    }

    @Test
    void aNullBaseYieldsOptionsWithOnlyTheOverrides() {
        ChatOptions options = new TurPromptOverrides(0.3, null, 20, null, null, null, null)
                .applyTo((ChatOptions) null, "m", PARAM);

        assertThat(options.getTemperature()).isEqualTo(0.3);
        assertThat(options.getMaxTokens()).isEqualTo(20);
        assertThat(options.getModel()).isNull();
    }

    @Test
    void jsonSchemaCarriesTheSchemaAndStrictFlag() {
        OpenAiChatModel.ResponseFormat spring = format("json_schema", Map.of("type", "object"), true)
                .toSpring(PARAM);

        assertThat(spring.getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(spring.getJsonSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(spring.getStrict()).isTrue();
    }

    @Test
    void jsonSchemaWithoutASchemaIsRefusedNamingTheCallersField() {
        TurPromptOverrides noSchema = format("json_schema", null, null);

        assertThatThrownBy(() -> noSchema.toSpring(PARAM))
                .isInstanceOfSatisfying(TurUnsupportedParameterException.class,
                        e -> assertThat(e.getParam()).isEqualTo(PARAM))
                .hasMessageContaining("schema");
    }

    @Test
    void unknownTypeIsRefused() {
        TurPromptOverrides yaml = format("yaml", null, null);

        assertThatThrownBy(() -> yaml.toSpring(PARAM))
                .isInstanceOf(TurUnsupportedParameterException.class)
                .hasMessageContaining("yaml");
    }

    @Test
    void jsonModeKeepsTheBaseOptionsOnTheOpenAiWire() {
        OpenAiChatOptions base = OpenAiChatOptions.builder().model("gpt").temperature(0.4).build();

        OpenAiChatOptions applied = (OpenAiChatOptions) format("json_object", null, null)
                .applyTo(base, "gpt", PARAM);

        assertThat(applied.getModel()).isEqualTo("gpt");
        assertThat(applied.getTemperature()).isEqualTo(0.4);
        assertThat(applied.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }
}
