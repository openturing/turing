/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.Locale;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

import tools.jackson.databind.ObjectMapper;

/**
 * T1090 — carries an OpenAI {@code response_format} from the gateway request to
 * the provider call. Support is read off the built model rather than a vendor
 * list: an instance whose options are {@link OpenAiChatOptions} speaks the
 * OpenAI wire (OpenAI, OpenAI-compatible, Gemini-OpenAI, Mistral, Cohere), so
 * JSON mode is passed through; any other model, and every Turing-routed model,
 * refuses the request with a 400 naming the field instead of answering prose.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurGatewayResponseFormat {

    static final String PARAM = "response_format";
    private static final String TEXT = "text";
    private static final String JSON_OBJECT = "json_object";
    private static final String JSON_SCHEMA = "json_schema";
    private static final ObjectMapper JSON = new ObjectMapper();

    private TurGatewayResponseFormat() {
    }

    /** True when the request asks for anything other than plain text. */
    static boolean requested(ResponseFormat format) {
        return format != null && !TEXT.equals(normalisedType(format));
    }

    /** Suffix that keeps a JSON-mode answer out of the plain-text cache slot. */
    static String cacheModelKey(String model, ResponseFormat format) {
        return requested(format) ? model + "|" + PARAM + "=" + normalisedType(format) : model;
    }

    /** Refuses a JSON-mode request on a path that answers without a provider call. */
    static void rejectIfRequested(ResponseFormat format, String model) {
        if (requested(format)) {
            throw new TurGatewayUnsupportedParameterException(PARAM,
                    PARAM + " is not supported for model " + model);
        }
    }

    /**
     * Returns {@code prompt} carrying the requested format on top of the model's
     * own defaults, or {@code prompt} untouched when plain text was asked for.
     * The defaults are copied whole (model, sampling, max tokens) so the result
     * does not depend on how a provider merges runtime options over its own.
     */
    static Prompt apply(Prompt prompt, ChatModel chatModel, ResponseFormat format, String model) {
        if (!requested(format)) {
            return prompt;
        }
        OpenAiChatModel.ResponseFormat springFormat = toSpring(format);
        if (!(chatModel.getOptions() instanceof OpenAiChatOptions defaults)) {
            throw new TurGatewayUnsupportedParameterException(PARAM,
                    PARAM + " is not supported by the provider behind model " + model);
        }
        OpenAiChatOptions options = defaults.mutate().responseFormat(springFormat).build();
        return new Prompt(prompt.getInstructions(), options);
    }

    static OpenAiChatModel.ResponseFormat toSpring(ResponseFormat format) {
        String type = normalisedType(format);
        if (JSON_OBJECT.equals(type)) {
            return OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                    .build();
        }
        if (JSON_SCHEMA.equals(type)) {
            if (format.json_schema() == null || format.json_schema().schema() == null) {
                throw new TurGatewayUnsupportedParameterException(PARAM,
                        PARAM + " of type json_schema needs json_schema.schema");
            }
            return OpenAiChatModel.ResponseFormat.builder()
                    .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(JSON.writeValueAsString(format.json_schema().schema()))
                    .strict(format.json_schema().strict())
                    .build();
        }
        throw new TurGatewayUnsupportedParameterException(PARAM,
                "Unknown " + PARAM + " type: " + format.type());
    }

    private static String normalisedType(ResponseFormat format) {
        return format.type() == null ? TEXT : format.type().trim().toLowerCase(Locale.ROOT);
    }
}
