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
package com.viglet.turing.genai.nativeapi;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

import lombok.extern.slf4j.Slf4j;

/**
 * T433 / §X.18.b — vendor-neutral helper for the native tool-execution loop:
 * indexes the agent's coexisting {@link ToolCallback}s by tool name, parses
 * each callback's JSON-Schema string into a {@code Map}, and executes a tool
 * call against the matching callback (returning the result or a structured
 * error string instead of throwing, so a misbehaving tool never aborts the
 * turn).
 *
 * <p>The vendor-specific tool-definition shapes (OpenAI {@code FunctionTool},
 * Anthropic custom {@code Tool}) and the loop control live in each provider's
 * service; this collaborator owns only the parts that are identical between
 * them — which keeps the two loops in sync on schema parsing and execution
 * semantics.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNativeFunctionToolSupport {

    private final TurProviderOptionsParser optionsParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurNativeFunctionToolSupport(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /**
     * Serialize a tool-call argument object (typically a {@code Map} decoded from
     * a provider's {@code tool_use} input) to a JSON string suitable for
     * {@link ToolCallback#call(String)}. Returns {@code "{}"} on failure so the
     * tool still runs with empty arguments rather than aborting the turn.
     */
    public String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.debug("[Native][Tools] could not serialize tool arguments: {}", e.getMessage());
            return "{}";
        }
    }

    /** Index callbacks by their tool-definition name (insertion order preserved). */
    public Map<String, ToolCallback> indexByName(ToolCallback[] callbacks) {
        Map<String, ToolCallback> index = new LinkedHashMap<>();
        if (callbacks == null) {
            return index;
        }
        for (ToolCallback callback : callbacks) {
            try {
                String name = callback.getToolDefinition().name();
                if (name != null && !name.isBlank()) {
                    index.put(name, callback);
                }
            } catch (RuntimeException e) {
                log.debug("[Native][Tools] could not read a tool name: {}", e.getMessage());
            }
        }
        return index;
    }

    /**
     * Parse a callback's {@code inputSchema()} (a JSON-Schema object string) into
     * a {@code Map}. Returns an empty object schema when the schema is blank or
     * unparseable, so a tool with no declared parameters still advertises a
     * valid {@code {"type":"object"}} shape.
     */
    public Map<String, Object> parseSchema(ToolCallback callback) {
        String schema;
        try {
            schema = callback.getToolDefinition().inputSchema();
        } catch (RuntimeException e) {
            schema = null;
        }
        Map<String, Object> parsed = optionsParser.parse(schema);
        if (parsed.isEmpty()) {
            parsed = new LinkedHashMap<>();
            parsed.put("type", "object");
        }
        return parsed;
    }

    /**
     * Execute the named tool with the given JSON argument string. Returns the
     * tool's textual result, or a short error message (never throws) so the
     * model can recover within the same turn.
     */
    public ToolOutcome execute(Map<String, ToolCallback> index, String toolName, String argumentsJson) {
        ToolCallback callback = index.get(toolName);
        if (callback == null) {
            log.warn("[Native][Tools] model called unknown tool '{}'", toolName);
            return new ToolOutcome("Error: tool '" + toolName + "' is not available.", true);
        }
        try {
            String result = callback.call(argumentsJson == null ? "{}" : argumentsJson);
            return new ToolOutcome(result == null ? "" : result, false);
        } catch (RuntimeException e) {
            log.warn("[Native][Tools] tool '{}' failed: {}", toolName, e.getMessage());
            return new ToolOutcome("Error executing '" + toolName + "': " + e.getMessage(), true);
        }
    }

    /** Result of one tool execution: its output and whether it errored. */
    public record ToolOutcome(String output, boolean error) {
    }
}
