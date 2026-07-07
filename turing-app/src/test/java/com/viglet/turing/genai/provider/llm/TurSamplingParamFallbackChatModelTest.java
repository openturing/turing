/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link TurSamplingParamFallbackChatModel#isSamplingParamRejection}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSamplingParamFallbackChatModelTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Anthropic: parameter deprecated for reasoning models
            "400: {\"error\":{\"message\":\"`temperature` is deprecated for this model.\"}}",
            // OpenAI o-series: unsupported value
            "Unsupported value: 'temperature' does not support 0.7 with this model.",
            // Anthropic: temperature + top_p both sent — the case that previously slipped through
            "400: {\"error\":{\"message\":\"`temperature` and `top_p` cannot both be specified for this model. Please use only one.\"}}",
    })
    void detectsSamplingParamRejections(String message) {
        assertThat(TurSamplingParamFallbackChatModel.isSamplingParamRejection(new RuntimeException(message)))
                .as("should recognise sampling-param rejection: %s", message)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Unrelated bad request — must NOT trigger the sampling-param fallback
            "401: {\"error\":{\"message\":\"invalid api key\"}}",
            // Names a sampling param but is not a rejection of it
            "temperature set to 0.7",
            // Rejection wording but no sampling param named
            "model does not support tool calling",
    })
    void ignoresUnrelatedErrors(String message) {
        assertThat(TurSamplingParamFallbackChatModel.isSamplingParamRejection(new RuntimeException(message)))
                .as("should NOT treat as sampling-param rejection: %s", message)
                .isFalse();
    }

    @Test
    void walksCauseChain() {
        var root = new RuntimeException(
                "`temperature` and `top_p` cannot both be specified for this model. Please use only one.");
        var wrapped = new IllegalStateException("stream failed", new RuntimeException("relay", root));

        assertThat(TurSamplingParamFallbackChatModel.isSamplingParamRejection(wrapped)).isTrue();
    }

    @Test
    void nullMessageDoesNotThrow() {
        assertThat(TurSamplingParamFallbackChatModel.isSamplingParamRejection(new RuntimeException())).isFalse();
    }

    @Test
    void retryStripsSamplingParamsFromPromptButKeepsOtherOptions() {
        // Primary rejects because temperature + top_p were both sent.
        ChatModel primary = new StubChatModel(p -> {
            throw new RuntimeException(
                    "`temperature` and `top_p` cannot both be specified for this model. Please use only one.");
        });
        AtomicReference<Prompt> retried = new AtomicReference<>();
        ChatModel withoutSampling = new StubChatModel(p -> {
            retried.set(p);
            return new ChatResponse(List.of());
        });

        var fallback = new TurSamplingParamFallbackChatModel(primary, withoutSampling);

        ChatOptions original = ToolCallingChatOptions.builder()
                .model("claude-opus-4-8")
                .maxTokens(512)
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .build();

        fallback.call(new Prompt(List.of(new UserMessage("hi")), original));

        assertThat(retried.get()).as("fallback must have retried against withoutSampling").isNotNull();
        ChatOptions sent = retried.get().getOptions();
        assertThat(sent.getTemperature()).as("temperature cleared").isNull();
        assertThat(sent.getTopP()).as("top_p cleared").isNull();
        assertThat(sent.getTopK()).as("top_k cleared").isNull();
        assertThat(sent.getModel()).as("model preserved").isEqualTo("claude-opus-4-8");
        assertThat(sent.getMaxTokens()).as("maxTokens preserved").isEqualTo(512);
    }

    /** Minimal {@link ChatModel} that runs a supplied function on {@code call}. */
    private static final class StubChatModel implements ChatModel {
        private final java.util.function.Function<Prompt, ChatResponse> onCall;

        StubChatModel(java.util.function.Function<Prompt, ChatResponse> onCall) {
            this.onCall = onCall;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return onCall.apply(prompt);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(onCall.apply(prompt));
        }
    }
}
