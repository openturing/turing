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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.viglet.turing.genai.gateway.TurOpenAiWire.JsonSchemaSpec;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

/**
 * T1090 — {@code response_format} reaches the provider options on an
 * OpenAI-wire model and is refused, naming the field, everywhere else.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayResponseFormatTest {

    private static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object", null);

    private static Prompt prompt() {
        return new Prompt(List.of(new UserMessage("name 3 colours as JSON")));
    }

    private static ChatModel modelWith(ChatOptions options) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(options);
        return model;
    }

    @Test
    void plainTextOrAbsentFormatLeavesThePromptUntouched() {
        Prompt prompt = prompt();
        ChatModel model = mock(ChatModel.class);

        assertThat(TurGatewayResponseFormat.apply(prompt, model, null, "gpt")).isSameAs(prompt);
        assertThat(TurGatewayResponseFormat.apply(prompt, model, new ResponseFormat("text", null), "gpt"))
                .isSameAs(prompt);
    }

    @Test
    void jsonObjectIsCarriedOnTopOfTheModelDefaults() {
        ChatModel model = modelWith(OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.2).build());

        Prompt applied = TurGatewayResponseFormat.apply(prompt(), model, JSON_OBJECT, "gpt-4o-mini");

        OpenAiChatOptions options = (OpenAiChatOptions) applied.getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
        assertThat(applied.getInstructions()).hasSize(1);
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
    void aProviderWithoutOpenAiOptionsIsRefusedNamingTheField() {
        ChatModel model = modelWith(ChatOptions.builder().model("claude").build());

        assertThatThrownBy(() -> TurGatewayResponseFormat.apply(prompt(), model, JSON_OBJECT, "claude"))
                .isInstanceOfSatisfying(TurGatewayUnsupportedParameterException.class,
                        e -> assertThat(e.getParam()).isEqualTo("response_format"))
                .hasMessageContaining("claude");
    }

    @Test
    void jsonModeHasItsOwnCacheSlot() {
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", null)).isEqualTo("gpt");
        assertThat(TurGatewayResponseFormat.cacheModelKey("gpt", JSON_OBJECT)).isNotEqualTo("gpt");
    }
}
