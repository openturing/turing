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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;

import lombok.extern.slf4j.Slf4j;

/**
 * Always-present platform tool {@code workspace_read} (T114, §IX.3.d).
 *
 * <p>The companion of {@link TurToolResultOffloadCallback}: when a large tool
 * result is offloaded to the workspace, the LLM gets a
 * {@code workspace://tool-results/...json} reference instead of the bytes. It
 * resolves that reference by calling this tool with the {@code key}, which
 * reads the blob back from the per-conversation {@link TurAgentWorkspace} and
 * returns it as text. The data only re-enters the prompt when the model
 * actually asks for it — that is the whole point of the offload pattern.
 *
 * <p>The tool is auto-added by {@link TurToolCallbackPipeline} whenever
 * offloading is active (enabled + a storage backend configured); it is not an
 * operator-selectable native tool. Scope (agent id + conversation id) is read
 * from the Spring AI {@link ToolContext}, the same keys
 * {@link TurCustomToolCallbackService} publishes; outside an active session the
 * tool degrades to a clear "unavailable" message rather than throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurWorkspaceReadToolCallback implements ToolCallback {

    public static final String TOOL_NAME = "workspace_read";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "key": {
                  "type": "string",
                  "description": "The workspace key to read, e.g. tool-results/search_site-1717430400000-7.json"
                }
              },
              "required": ["key"]
            }""";

    private static final String DEFAULT_DESCRIPTION = """
            Read the full contents of a workspace artifact by its key. Use this \
            to resolve a `workspace://<key>` reference that a previous tool \
            returned when its result was too large to inline (for example a \
            large search dump or JSON catalog under `tool-results/`). Pass the \
            key exactly as given in the reference.""";

    private final TurAgentWorkspace workspace;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolDefinition definition;

    public TurWorkspaceReadToolCallback(TurAgentWorkspace workspace) {
        this.workspace = workspace;
        this.definition = new DefaultToolDefinition(TOOL_NAME, DEFAULT_DESCRIPTION, INPUT_SCHEMA);
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
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String agentId = contextValue(toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID);
        String conversationId = contextValue(toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        if (agentId == null || conversationId == null) {
            return "workspace_read is unavailable: no active conversation context.";
        }
        String key = parseKey(toolInput);
        if (key == null) {
            return "workspace_read requires a non-blank 'key' argument.";
        }
        try {
            Optional<byte[]> bytes = workspace.get(agentId, conversationId, key);
            if (bytes.isEmpty()) {
                return "No workspace artifact found at key '" + key + "'.";
            }
            return new String(bytes.get(), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            log.warn("[workspace_read] conv={} key='{}' read failed: {}", conversationId, key, e.getMessage());
            return "Failed to read workspace artifact at key '" + key + "': " + e.getMessage();
        }
    }

    private String parseKey(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> args = objectMapper.readValue(toolInput, Map.class);
            Object key = args.get("key");
            if (key == null) {
                return null;
            }
            String text = key.toString().strip();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            // The model occasionally passes a bare string instead of JSON.
            String text = toolInput.strip();
            return text.isEmpty() ? null : text;
        }
    }

    private static String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) {
            return null;
        }
        Object value = ctx.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
