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

import org.springframework.ai.openai.OpenAiChatModel;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

import tools.jackson.databind.ObjectMapper;

/**
 * T1090 — an OpenAI {@code response_format} on a gateway request. Support is
 * read off the built model rather than a vendor list: an instance whose options
 * are {@code OpenAiChatOptions} speaks the OpenAI wire (OpenAI, OpenAI-compatible,
 * Gemini-OpenAI, Mistral, Cohere), so JSON mode is passed through by
 * {@link TurGatewayPromptOptions}; any other model, and every Turing-routed
 * model, refuses the request with a 400 naming the field instead of answering
 * prose.
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
