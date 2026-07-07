/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * F.10 / §X.11.c — T173. Runs a turn through the OpenAI <b>Responses API</b>
 * with <b>strict structured outputs</b>: {@code text.format} is a
 * {@code json_schema} config with {@code strict: true}, so the model's reply is
 * a JSON document validated against the supplied schema <em>by construction</em>.
 *
 * <p>This retires the legacy {@code json_object} JSON-mode workarounds (the
 * gpt-4o-mini truncation / prose-around-JSON / dropped-comma failures recorded
 * in agent memory): with a strict schema the provider guarantees a parseable,
 * schema-conforming object — no defensive brace-scan or retry needed.
 *
 * <p>The schema is passed as a raw JSON-Schema {@code Map} (not a Java class),
 * because Turing's schemas are dynamic — authoring an arbitrary entity type, a
 * judge verdict, a router decision. {@code strict: true} requires the schema to
 * set {@code additionalProperties: false} and list every property in
 * {@code required}; the caller owns that (see {@link #strictObjectSchema}).
 *
 * <p>Gated by {@link com.viglet.turing.genai.structuredoutputs.TurStructuredOutputsResolver};
 * a non-OpenAI instance yields {@link Optional#empty()} so the caller can fall
 * back (or route to the Anthropic leg, T174).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiStructuredOutputsService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final TurNativeProviderClient providerClient;

    public TurOpenAiStructuredOutputsService(TurNativeProviderClient providerClient) {
        this.providerClient = providerClient;
    }

    /**
     * Complete {@code userInput} as a JSON object conforming to {@code schema}.
     *
     * @param instance     the OpenAI-backed LLM instance; a non-OpenAI one → empty
     * @param systemPrompt optional system instructions
     * @param userInput    the user message
     * @param schemaName   a short identifier for the schema (a–z, 0–9, _, -)
     * @param schema       the JSON Schema as a map (must be strict-compatible)
     * @return the validated JSON text, or empty when the instance is not OpenAI /
     *         the model returned no content
     */
    public Optional<String> complete(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema) {
        return complete(instance, systemPrompt, userInput, schemaName, schema, true);
    }

    /**
     * As {@link #complete(TurLLMInstance, String, String, String, Map)} but with
     * an explicit {@code strict} flag. {@code strict=true} (T173 default)
     * guarantees schema conformance but requires a strict-mode schema
     * ({@code additionalProperties:false} + all properties {@code required}).
     * {@code strict=false} still forces a JSON-object reply matching the
     * schema's shape (no prose, no fences) but tolerates arbitrary
     * (e.g. converter-generated) schemas — used by the AI Authoring path (T426).
     */
    public Optional<String> complete(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema, boolean strict) {
        Optional<OpenAIClient> client = providerClient.openAi(instance);
        if (client.isEmpty()) {
            log.debug("[Native][OpenAI-Structured] instance '{}' is not OpenAI — skipping structured path",
                    instance == null ? null : instance.getId());
            return Optional.empty();
        }
        long t0 = System.currentTimeMillis();
        ResponseCreateParams params = buildParams(instance, systemPrompt, userInput, schemaName, schema, strict);
        Response response = client.get().responses().create(params);
        Optional<String> text = extractText(response);
        log.info("[Native][OpenAI-Structured] instance '{}' model '{}' schema '{}' -> {} chars in {} ms",
                instance.getId(), resolveModel(instance), schemaName, text.map(String::length).orElse(0),
                System.currentTimeMillis() - t0);
        return text;
    }

    ResponseCreateParams buildParams(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema) {
        return buildParams(instance, systemPrompt, userInput, schemaName, schema, true);
    }

    ResponseCreateParams buildParams(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema, boolean strict) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveModel(instance))
                .input(userInput == null ? "" : userInput)
                .text(jsonSchemaText(schemaName, schema, strict));
        if (StringUtils.hasText(systemPrompt)) {
            builder.instructions(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
        }
        return builder.build();
    }

    /** Build the {@code text} config carrying a {@code json_schema} format. */
    ResponseTextConfig jsonSchemaText(String schemaName, Map<String, Object> schema, boolean strict) {
        ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder =
                ResponseFormatTextJsonSchemaConfig.Schema.builder();
        if (schema != null) {
            schema.forEach((key, value) -> schemaBuilder.putAdditionalProperty(key, JsonValue.from(value)));
        }
        ResponseFormatTextJsonSchemaConfig config = ResponseFormatTextJsonSchemaConfig.builder()
                .name(StringUtils.hasText(schemaName) ? schemaName : "response")
                .schema(schemaBuilder.build())
                .strict(strict)
                .build();
        return ResponseTextConfig.builder().format(config).build();
    }

    /**
     * Helper that wraps a property map into an object schema satisfying OpenAI's
     * strict-mode rules: {@code type: object}, {@code additionalProperties: false},
     * and every property listed in {@code required}.
     */
    public static Map<String, Object> strictObjectSchema(Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", properties.keySet().stream().sorted().toList());
    }

    private Optional<String> extractText(Response response) {
        if (response == null) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    text.append(content.asOutputText().text());
                }
            }
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }
}
