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
package com.viglet.turing.genai.nativeapi.anthropic;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * F.10 / §X.11.d — T174. The Claude analog of T173's strict structured outputs.
 *
 * <p>Anthropic has no {@code response_format}; the canonical way to force a
 * schema-validated JSON object out of Claude is <b>forced single-tool use</b>:
 * declare one tool whose {@code input_schema} is the desired shape and pin
 * {@code tool_choice} to it, so the model's only move is to emit a
 * {@code tool_use} block whose {@code input} <em>is</em> the structured object —
 * validated against the schema by construction, no prose, no fences.
 *
 * <p><b>ZDR-cached schemas:</b> the tool (hence the schema) carries
 * {@code cache_control: ephemeral}, so a stable schema reused across turns is
 * served from Anthropic's prompt cache rather than re-sent each call — the
 * Anthropic counterpart to OpenAI's strict-schema reuse.
 *
 * <p>Gated by the cross-provider
 * {@link com.viglet.turing.genai.structuredoutputs.TurStructuredOutputsResolver}
 * (shared with T173). A non-Anthropic instance yields {@link Optional#empty()}
 * so the caller can fall back (or route to the OpenAI leg).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnthropicStructuredOutputsService {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final long DEFAULT_MAX_TOKENS = 4096L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TurNativeProviderClient providerClient;
    private final TurProviderOptionsParser optionsParser;

    public TurAnthropicStructuredOutputsService(TurNativeProviderClient providerClient,
            TurProviderOptionsParser optionsParser) {
        this.providerClient = providerClient;
        this.optionsParser = optionsParser;
    }

    /**
     * Complete {@code userInput} as a JSON object conforming to {@code schema}.
     *
     * @param instance     the Anthropic-backed LLM instance; a non-Anthropic one → empty
     * @param systemPrompt optional system instructions
     * @param userInput    the user message
     * @param schemaName   the tool name (a–z, 0–9, _, -); also the schema identifier
     * @param schema       the JSON Schema as a map (its {@code properties}/{@code required} become the tool input schema)
     * @return the validated JSON text, or empty when not Anthropic / no tool_use block came back
     */
    public Optional<String> complete(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema) {
        Optional<AnthropicClient> client = providerClient.anthropic(instance);
        if (client.isEmpty()) {
            log.debug("[Native][Anthropic-Structured] instance '{}' is not Anthropic — skipping structured path",
                    instance == null ? null : instance.getId());
            return Optional.empty();
        }
        long t0 = System.currentTimeMillis();
        MessageCreateParams params = buildParams(instance, systemPrompt, userInput, schemaName, schema);
        Message response = client.get().messages().create(params);
        Optional<String> json = extractToolInput(response);
        log.info("[Native][Anthropic-Structured] instance '{}' model '{}' schema '{}' -> {} chars in {} ms",
                instance.getId(), resolveModel(instance), schemaName, json.map(String::length).orElse(0),
                System.currentTimeMillis() - t0);
        return json;
    }

    MessageCreateParams buildParams(TurLLMInstance instance, String systemPrompt, String userInput,
            String schemaName, Map<String, Object> schema) {
        String toolName = StringUtils.hasText(schemaName) ? schemaName : "response";
        Tool tool = Tool.builder()
                .name(toolName)
                .description("Return the answer as a structured object matching the schema.")
                .inputSchema(toInputSchema(schema))
                // ZDR-cached schema: a stable schema is served from the prompt cache.
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(instance))
                .maxTokens(resolveMaxTokens(instance))
                .addUserMessage(userInput == null ? "" : userInput)
                .addTool(tool)
                // Force the model to call exactly this tool — its input is the result.
                .toolToolChoice(toolName);
        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            applyTemperature(builder, instance.getTemperature());
        }
        return builder.build();
    }

    /**
     * Apply the per-instance temperature. The Anthropic SDK deprecated
     * {@code temperature(double)} because models after Claude Opus 4.6 reject any
     * non-1.0 temperature (HTTP 400). Turing still honours the configured
     * temperature deliberately — for the many older Claude models, and
     * OpenAI-compatible Anthropic proxies, that accept it — so the deprecation is
     * suppressed here, scoped to this one call to keep the signal for any other
     * deprecated API elsewhere.
     */
    @SuppressWarnings("deprecation")
    private static void applyTemperature(MessageCreateParams.Builder builder, double temperature) {
        builder.temperature(temperature);
    }

    /** Map a JSON-Schema {@code properties}/{@code required} onto {@link Tool.InputSchema}. */
    Tool.InputSchema toInputSchema(Map<String, Object> schema) {
        Tool.InputSchema.Builder builder = Tool.InputSchema.builder();
        if (schema == null) {
            return builder.build();
        }
        if (schema.get("properties") instanceof Map<?, ?> propertyMap) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                props.putAdditionalProperty(String.valueOf(entry.getKey()),
                        JsonValue.from(entry.getValue()));
            }
            builder.properties(props.build());
        }
        if (schema.get("required") instanceof java.util.List<?> requiredList) {
            builder.required(requiredList.stream().map(String::valueOf).toList());
        }
        return builder.build();
    }

    /** First {@code tool_use} block's input, serialized back to a JSON string. */
    private Optional<String> extractToolInput(Message response) {
        if (response == null) {
            return Optional.empty();
        }
        for (ContentBlock block : response.content()) {
            if (block.isToolUse()) {
                Object input = block.asToolUse()._input().convert(Object.class);
                return Optional.of(JSON.writeValueAsString(input));
            }
        }
        return Optional.empty();
    }

    private long resolveMaxTokens(TurLLMInstance instance) {
        Integer configured = optionsParser.intValue(
                optionsParser.parse(instance.getProviderOptionsJson()), "maxTokens");
        return configured != null && configured > 0 ? configured.longValue() : DEFAULT_MAX_TOKENS;
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }
}
