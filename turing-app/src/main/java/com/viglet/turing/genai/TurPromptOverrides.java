/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import tools.jackson.databind.ObjectMapper;

/**
 * T1102 — what a chat caller set for one call, applied over the model's own
 * options: {@code temperature}, {@code topP}, {@code maxTokens} and {@code stop}
 * on every vendor, and a JSON response format where the model speaks the OpenAI
 * wire. Shared by the OpenAI-compatible gateway (T1090, T1099) and the native
 * {@code /api/v2/llm/{id}/chat} endpoint, so both read a request the same way.
 *
 * <p>Sampling is best-effort, as the OpenAI contract treats it: every vendor's
 * options take the four portable fields, and a reasoning model that rejects
 * sampling is retried without it by {@code TurSamplingParamFallbackChatModel}.
 * The response format is not advisory: support is read off the options (an
 * {@link OpenAiChatOptions} speaks the OpenAI wire: OpenAI, OpenAI-compatible,
 * Gemini-OpenAI, Mistral, Cohere), and anything else is refused with
 * {@link TurUnsupportedParameterException} instead of answering prose.</p>
 *
 * <p>The base options are copied whole through {@code mutate()} (model,
 * sampling, tool callbacks) so the result does not depend on how a provider
 * merges runtime options over its own.</p>
 *
 * @param temperature    sampling temperature, or {@code null} to keep the model's
 * @param topP           nucleus sampling, or {@code null}
 * @param maxTokens      output token cap, or {@code null}
 * @param stop           stop sequences, or {@code null}/empty
 * @param responseFormat {@code text} (or {@code null}), {@code json_object} or
 *                       {@code json_schema}
 * @param responseSchema the JSON schema, required with {@code json_schema}
 * @param strict         whether a {@code json_schema} is enforced strictly
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptOverrides(Double temperature, Double topP, Integer maxTokens, List<String> stop,
        String responseFormat, Map<String, Object> responseSchema, Boolean strict) {

    private static final String TEXT = "text";
    private static final String JSON_OBJECT = "json_object";
    private static final String JSON_SCHEMA = "json_schema";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** True when a response format other than plain text is asked for. */
    public boolean jsonMode() {
        return !TEXT.equals(formatType());
    }

    /** True when the caller set nothing, so the prompt is built as it always was. */
    public boolean isEmpty() {
        return !jsonMode() && temperature == null && topP == null && maxTokens == null
                && (stop == null || stop.isEmpty());
    }

    /** The normalised format type: {@code text} when none was given. */
    public String formatType() {
        return responseFormat == null || responseFormat.isBlank()
                ? TEXT
                : responseFormat.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code prompt} carrying these overrides on top of {@code chatModel}'s own
     * options, or {@code prompt} untouched when nothing was set.
     *
     * @param model       how the error names the model
     * @param formatParam how the error names the response-format field
     */
    public Prompt applyTo(Prompt prompt, ChatModel chatModel, String model, String formatParam) {
        if (isEmpty()) {
            return prompt;
        }
        return new Prompt(prompt.getInstructions(), applyTo(chatModel.getOptions(), model, formatParam));
    }

    /**
     * {@code base} with these overrides applied, or {@code base} itself when
     * nothing was set. A {@code null} base yields options with only the
     * overrides, which the model merges over its defaults.
     */
    public ChatOptions applyTo(ChatOptions base, String model, String formatParam) {
        if (isEmpty()) {
            return base;
        }
        boolean json = jsonMode();
        if (json && !(base instanceof OpenAiChatOptions)) {
            throw new TurUnsupportedParameterException(formatParam,
                    formatParam + " is not supported by the provider behind model " + model);
        }
        if (base instanceof OpenAiChatOptions openAi) {
            OpenAiChatOptions.Builder builder = openAi.mutate();
            sampling(builder);
            if (json) {
                builder.responseFormat(toSpring(formatParam));
            }
            return builder.build();
        }
        ChatOptions.Builder<?> builder = base != null ? base.mutate() : ChatOptions.builder();
        sampling(builder);
        return builder.build();
    }

    /** The Spring AI form of the requested JSON format; refuses an unknown or incomplete one. */
    public OpenAiChatModel.ResponseFormat toSpring(String formatParam) {
        String type = formatType();
        if (JSON_OBJECT.equals(type)) {
            return OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                    .build();
        }
        if (JSON_SCHEMA.equals(type)) {
            if (responseSchema == null) {
                throw new TurUnsupportedParameterException(formatParam,
                        formatParam + " of type json_schema needs a schema");
            }
            return OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(JSON.writeValueAsString(responseSchema))
                    .strict(strict)
                    .build();
        }
        throw new TurUnsupportedParameterException(formatParam,
                "Unknown " + formatParam + " type: " + responseFormat);
    }

    private void sampling(ChatOptions.Builder<?> builder) {
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (stop != null && !stop.isEmpty()) {
            builder.stopSequences(stop);
        }
    }
}
