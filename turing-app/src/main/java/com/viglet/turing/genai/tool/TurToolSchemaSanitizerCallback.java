/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Decorator that strips {@code null}-valued entries from a tool's input JSON
 * Schema before it reaches the {@code ChatModel}.
 * <p>
 * Motivation: the Google GenAI ({@code Gemini}/{@code Vertex AI}) binding maps
 * each tool parameter schema into {@code com.google.genai.types.Schema} via an
 * AutoValue builder that wraps fields with {@link java.util.Optional#of}, which
 * throws {@link NullPointerException} on a {@code null} value. A tool whose
 * schema carries {@code "default": null} (common in externally-defined MCP /
 * skill tools) therefore crashes request assembly
 * ({@code GoogleGenAiChatModel.jsonToSchema}) with a Jackson
 * {@code DatabindException (was NullPointerException)}. OpenAI and Anthropic
 * tolerate the same schema, so this is Gemini-specific breakage.
 * <p>
 * A {@code null}-valued JSON Schema entry carries no information (it is not a
 * valid default/description/format), so removing it is semantically safe for
 * every provider — hence this runs unconditionally in
 * {@link TurToolCallbackPipeline}. Real (non-null) defaults are preserved.
 * Parsing failures fall back to the original schema so tools are never dropped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurToolSchemaSanitizerCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final ToolCallback delegate;
    private final ToolDefinition sanitizedDefinition;

    private TurToolSchemaSanitizerCallback(ToolCallback delegate, String sanitizedSchema) {
        this.delegate = delegate;
        ToolDefinition original = delegate.getToolDefinition();
        this.sanitizedDefinition = DefaultToolDefinition.builder()
                .name(original.name())
                .description(original.description())
                .inputSchema(sanitizedSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return sanitizedDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }

    /**
     * Wraps callbacks whose input schema contains at least one {@code null}
     * value; leaves the rest untouched.
     */
    public static ToolCallback[] wrap(ToolCallback[] callbacks) {
        ToolCallback[] result = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            result[i] = maybeWrap(callbacks[i]);
        }
        return result;
    }

    private static ToolCallback maybeWrap(ToolCallback callback) {
        String schema = callback.getToolDefinition().inputSchema();
        if (!StringUtils.hasText(schema)) {
            return callback;
        }
        try {
            JsonNode root = MAPPER.readTree(schema);
            if (!removeNulls(root)) {
                return callback;
            }
            return new TurToolSchemaSanitizerCallback(callback, MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            log.debug("Could not sanitize input schema for tool '{}': {}",
                    callback.getToolDefinition().name(), e.getMessage());
            return callback;
        }
    }

    /**
     * Recursively removes object entries whose value is JSON {@code null}.
     * Array elements are recursed into but not removed (dropping positional
     * elements would change list semantics). Returns {@code true} when the
     * tree was modified.
     */
    private static boolean removeNulls(JsonNode node) {
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            List<String> nullKeys = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    nullKeys.add(entry.getKey());
                } else {
                    changed |= removeNulls(value);
                }
            }
            for (String key : nullKeys) {
                object.remove(key);
                changed = true;
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                changed |= removeNulls(item);
            }
        }
        return changed;
    }
}
