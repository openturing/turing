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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;
import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;

/**
 * T1099 / T1090 — what an OpenAI client sets on the request reaches the
 * provider on top of the instance defaults: sampling on every vendor,
 * {@code response_format} only on the OpenAI wire.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayPromptOptionsTest {

    private static final List<WireMessage> MESSAGES = List.of(new WireMessage("user", "hi"));

    private static Prompt prompt() {
        return new Prompt(List.of(new UserMessage("hi")));
    }

    private static ChatModel modelWith(ChatOptions options) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(options);
        return model;
    }

    private static ChatCompletionRequest request(Double temperature, Double topP, Integer maxTokens,
            List<String> stop, ResponseFormat format) {
        return new ChatCompletionRequest("gpt-4o-mini", MESSAGES, false, temperature, topP, maxTokens, stop,
                format);
    }

    @Test
    void aRequestThatSetsNothingLeavesThePromptUntouched() {
        Prompt prompt = prompt();

        assertThat(TurGatewayPromptOptions.apply(prompt, mock(ChatModel.class),
                request(null, null, null, null, null))).isSameAs(prompt);
        assertThat(TurGatewayPromptOptions.apply(prompt, mock(ChatModel.class),
                request(null, null, null, List.of(), new ResponseFormat("text", null)))).isSameAs(prompt);
    }

    @Test
    void samplingOverridesTheOpenAiDefaultsAndKeepsTheRest() {
        ChatModel model = modelWith(OpenAiChatOptions.builder()
                .model("gpt-4o-mini").temperature(0.7).topP(0.9).maxTokens(1000).build());

        Prompt applied = TurGatewayPromptOptions.apply(prompt(), model,
                request(0.0, null, 50, List.of("END"), null));

        OpenAiChatOptions options = (OpenAiChatOptions) applied.getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(options.getTemperature()).isEqualTo(0.0);
        assertThat(options.getTopP()).isEqualTo(0.9);
        assertThat(options.getMaxTokens()).isEqualTo(50);
        assertThat(options.getStopSequences()).containsExactly("END");
        assertThat(options.getResponseFormat()).isNull();
    }

    @Test
    void jsonModeAndSamplingTravelTogetherOnTheOpenAiWire() {
        ChatModel model = modelWith(OpenAiChatOptions.builder().model("gpt-4o-mini").build());

        Prompt applied = TurGatewayPromptOptions.apply(prompt(), model,
                request(0.2, null, null, null, new ResponseFormat("json_object", null)));

        OpenAiChatOptions options = (OpenAiChatOptions) applied.getOptions();
        assertThat(options.getTemperature()).isEqualTo(0.2);
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void samplingReachesAVendorWithoutOpenAiOptions() {
        ChatModel model = modelWith(ChatOptions.builder().model("claude").temperature(1.0).build());

        Prompt applied = TurGatewayPromptOptions.apply(prompt(), model,
                request(0.1, 0.5, 64, List.of("STOP"), null));

        ChatOptions options = applied.getOptions();
        assertThat(options.getModel()).isEqualTo("claude");
        assertThat(options.getTemperature()).isEqualTo(0.1);
        assertThat(options.getTopP()).isEqualTo(0.5);
        assertThat(options.getMaxTokens()).isEqualTo(64);
        assertThat(options.getStopSequences()).containsExactly("STOP");
    }

    @Test
    void jsonModeOnAVendorWithoutOpenAiOptionsIsRefusedNamingTheField() {
        ChatModel model = modelWith(ChatOptions.builder().model("claude").build());

        assertThatThrownBy(() -> TurGatewayPromptOptions.apply(prompt(), model,
                request(null, null, null, null, new ResponseFormat("json_object", null))))
                .isInstanceOfSatisfying(TurGatewayUnsupportedParameterException.class,
                        e -> assertThat(e.getParam()).isEqualTo("response_format"));
    }
}
