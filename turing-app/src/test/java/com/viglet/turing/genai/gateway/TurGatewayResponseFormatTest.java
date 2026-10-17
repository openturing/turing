/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;

import com.viglet.turing.genai.gateway.TurOpenAiWire.JsonSchemaSpec;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

/**
 * T1090 — the {@code response_format} mapping; how it reaches the provider is
 * {@link TurGatewayPromptOptionsTest}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayResponseFormatTest {

    @Test
    void textOrAbsentIsNotRequested() {
        assertThat(TurGatewayResponseFormat.requested(null)).isFalse();
        assertThat(TurGatewayResponseFormat.requested(new ResponseFormat("text", null))).isFalse();
        assertThat(TurGatewayResponseFormat.requested(new ResponseFormat("json_object", null))).isTrue();
    }

    @Test
    void jsonSchemaCarriesTheSchemaAndStrictFlag() {
        ResponseFormat format = new ResponseFormat("json_schema",
                new JsonSchemaSpec("labels", Map.of("type", "object"), true));

        OpenAiChatModel.ResponseFormat spring = TurGatewayResponseFormat.toSpring(format);

        assertThat(spring.getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
        assertThat(spring.getJsonSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(spring.getStrict()).isTrue();
    }

    @Test
    void jsonSchemaWithoutASchemaIsRefused() {
        assertThatThrownBy(() -> TurGatewayResponseFormat.toSpring(new ResponseFormat("json_schema", null)))
                .isInstanceOf(TurGatewayUnsupportedParameterException.class)
                .hasMessageContaining("json_schema.schema");
    }

    @Test
    void unknownTypeIsRefused() {
        assertThatThrownBy(() -> TurGatewayResponseFormat.toSpring(new ResponseFormat("yaml", null)))
                .isInstanceOf(TurGatewayUnsupportedParameterException.class)
                .hasMessageContaining("yaml");
    }

    @Test
    void jsonModeHasItsOwnCacheSlot() {
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", null)).isEqualTo("gpt");
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", new ResponseFormat("json_object", null)))
                .isNotEqualTo("gpt");
    }
}
