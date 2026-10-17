/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionRequest;

/**
 * T1099 — carries what an OpenAI client set on the request to the provider
 * call, on top of the instance's own defaults: {@code temperature},
 * {@code top_p}, {@code max_tokens} and {@code stop} on every vendor, and
 * {@code response_format} (T1090) where the model speaks the OpenAI wire.
 *
 * <p>Sampling is best-effort, as the OpenAI contract treats it: every vendor's
 * options take the four portable fields, and a reasoning model that rejects
 * sampling is retried without it by {@code TurSamplingParamFallbackChatModel}.
 * {@code response_format} is not advisory, so a model that cannot honour it is
 * refused by {@link TurGatewayResponseFormat}.</p>
 *
 * <p>The defaults are copied whole through {@code mutate()} (model, sampling,
 * max tokens) so the result does not depend on how a provider merges runtime
 * options over its own.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurGatewayPromptOptions {

    private TurGatewayPromptOptions() {
    }

    /** {@code prompt} carrying the request's options, or untouched when it set none. */
    static Prompt apply(Prompt prompt, ChatModel chatModel, ChatCompletionRequest request) {
        boolean jsonMode = TurGatewayResponseFormat.requested(request.response_format());
        if (!jsonMode && !setsSampling(request)) {
            return prompt;
        }
        ChatOptions defaults = chatModel.getOptions();
        if (jsonMode && !(defaults instanceof OpenAiChatOptions)) {
            throw new TurGatewayUnsupportedParameterException(TurGatewayResponseFormat.PARAM,
                    TurGatewayResponseFormat.PARAM + " is not supported by the provider behind model "
                            + request.model());
        }
        ChatOptions options;
        if (defaults instanceof OpenAiChatOptions openAi) {
            OpenAiChatOptions.Builder builder = openAi.mutate();
            sampling(builder, request);
            if (jsonMode) {
                builder.responseFormat(TurGatewayResponseFormat.toSpring(request.response_format()));
            }
            options = builder.build();
        } else {
            ChatOptions.Builder<?> builder = defaults != null ? defaults.mutate() : ChatOptions.builder();
            sampling(builder, request);
            options = builder.build();
        }
        return new Prompt(prompt.getInstructions(), options);
    }

    private static boolean setsSampling(ChatCompletionRequest request) {
        return request.temperature() != null || request.top_p() != null || request.max_tokens() != null
                || (request.stop() != null && !request.stop().isEmpty());
    }

    private static void sampling(ChatOptions.Builder<?> builder, ChatCompletionRequest request) {
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.top_p() != null) {
            builder.topP(request.top_p());
        }
        if (request.max_tokens() != null) {
            builder.maxTokens(request.max_tokens());
        }
        if (request.stop() != null && !request.stop().isEmpty()) {
            builder.stopSequences(request.stop());
        }
    }
}
