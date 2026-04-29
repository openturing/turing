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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds Spring AI {@link ToolCallback} instances backed by user-defined
 * Groovy scripts. Each {@link TurCustomTool} becomes one callback whose
 * {@code call(String)} parses the LLM JSON arguments, binds them into the
 * Groovy script, evaluates, and returns the script result as a string.
 *
 * <p>Compiled scripts are cached per tool id and invalidated when the
 * source changes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurCustomToolCallbackService {

    private final TurCustomToolRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedScript> scriptCache = new ConcurrentHashMap<>();

    public TurCustomToolCallbackService(TurCustomToolRepository repository) {
        this.repository = repository;
    }

    /** Get tool callbacks only for the specified custom tools (filtered by agent configuration). */
    public ToolCallback[] getToolCallbacks(Set<TurCustomTool> customTools) {
        if (customTools == null || customTools.isEmpty()) {
            return new ToolCallback[0];
        }
        Set<String> allowedIds = customTools.stream()
                .map(TurCustomTool::getId)
                .collect(Collectors.toSet());

        return repository.findAll().stream()
                .filter(t -> t.getEnabled() == 1 && allowedIds.contains(t.getId()))
                .map(this::buildCallback)
                .toArray(ToolCallback[]::new);
    }

    /** All enabled custom tool callbacks across the platform. */
    public ToolCallback[] getToolCallbacks() {
        return repository.findAll().stream()
                .filter(t -> t.getEnabled() == 1)
                .map(this::buildCallback)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback buildCallback(TurCustomTool tool) {
        return new GroovyToolCallback(tool);
    }

    /* ---------- JSON Schema construction ---------- */

    private String buildInputSchema(TurCustomTool tool) {
        List<ParameterDef> params = readParameters(tool);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ParameterDef p : params) {
            properties.put(p.name(), Map.of("type", normalizeType(p.type())));
            required.add(p.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("[CustomTool] Failed to build input schema for tool '{}'", tool.getTitle(), e);
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    private List<ParameterDef> readParameters(TurCustomTool tool) {
        if (tool.getParametersJson() == null || tool.getParametersJson().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tool.getParametersJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[CustomTool] Could not parse parametersJson for '{}': {}", tool.getTitle(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "string";
        }
        return switch (type.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    /* ---------- Groovy execution ---------- */

    @SuppressWarnings("unchecked")
    private CachedScript scriptFor(TurCustomTool tool) {
        String id = tool.getId();
        int hash = tool.getGroovyScript() == null ? 0 : tool.getGroovyScript().hashCode();
        CachedScript cached = scriptCache.get(id);
        if (cached != null && cached.scriptHash == hash) {
            return cached;
        }
        GroovyShell shell = new GroovyShell();
        Class<? extends Script> clazz = (Class<? extends Script>) shell.getClassLoader().parseClass(
                tool.getGroovyScript() == null ? "" : tool.getGroovyScript(),
                "TurCustomTool_" + id + ".groovy");
        CachedScript fresh = new CachedScript(clazz, hash);
        scriptCache.put(id, fresh);
        return fresh;
    }

    String executeGroovy(TurCustomTool tool, String toolInput) throws ReflectiveOperationException {
        Map<String, Object> args = parseArgs(toolInput);
        CachedScript cached = scriptFor(tool);
        Binding binding = new Binding(new HashMap<>(args));
        binding.setVariable("args", args);
        Script script = cached.scriptClass.getDeclaredConstructor().newInstance();
        script.setBinding(binding);
        Object result = script.run();
        return result == null ? "" : result.toString();
    }

    private Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[CustomTool] Could not parse toolInput as JSON: {}", e.getMessage());
            return Map.of("input", toolInput);
        }
    }

    /* ---------- Inner types ---------- */

    public record ParameterDef(String name, String type) {
    }

    private record CachedScript(Class<? extends Script> scriptClass, int scriptHash) {
    }

    /**
     * Spring AI ToolCallback adapter for a single TurCustomTool. Outer
     * service is captured so we can reuse parser/cache logic.
     */
    final class GroovyToolCallback implements ToolCallback {
        private final TurCustomTool tool;
        private final ToolDefinition definition;

        GroovyToolCallback(TurCustomTool tool) {
            this.tool = tool;
            this.definition = new DefaultToolDefinition(
                    sanitizeName(tool.getTitle()),
                    tool.getLlmDescription() == null ? "" : tool.getLlmDescription(),
                    buildInputSchema(tool));
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return new DefaultToolMetadata(false);
        }

        @Override
        public String call(String toolInput) {
            try {
                return executeGroovy(tool, toolInput);
            } catch (Exception e) {
                log.error("[CustomTool] '{}' failed: {}", tool.getTitle(), e.getMessage(), e);
                return "Error executing tool: " + e.getMessage();
            }
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

    /** LLMs reject tool names with spaces; the title is friendlier so we coerce. */
    static String sanitizeName(String title) {
        if (title == null || title.isBlank()) {
            return "custom_tool";
        }
        String sanitized = title.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "custom_tool" : sanitized;
    }
}
