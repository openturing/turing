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

import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T425 / §XXI.1 — the {@code mcp} binding exposed to the Groovy custom-tool
 * sandbox. Lets a Custom Tool invoke the tools of an MCP server <em>attached to
 * the same agent</em>, reusing that one integration instead of re-implementing
 * a parallel direct-REST call to the same backend.
 *
 * <p>Motivation: the Aula-Relâmpago tool queried the Insper DSpace via
 * {@code http.getJson(...)} while the chat path used the {@code dspace_*} MCP
 * tools. The Insper Akamai WAF allowlists known library User-Agents and 403s
 * the JDK default {@code Java/<version>} that Spring {@code RestClient} sends,
 * so the direct-REST tool got blocked while the MCP server (honest
 * {@code python-httpx} UA) worked. Routing the tool through {@code mcp.call(...)}
 * reuses the working, single integration — no duplicated endpoint/auth, no
 * UA forging in the JVM.
 *
 * <p>Usage from a custom tool script:
 * <pre>{@code
 *   def raw  = mcp.call("dspace_search_items", [query: term, size: 8])
 *   def data = new groovy.json.JsonSlurper().parseText(raw)
 * }</pre>
 *
 * <p>Returns the MCP tool's raw (typically JSON) result string for the script
 * to parse. Errors are returned as a small JSON {@code {"error": "..."}} string
 * rather than thrown — the custom-tool convention is errors-as-text so the
 * script (and the model) can react instead of aborting the turn.
 *
 * <p>No-op when the tool runs without an agent context (unit tests, anonymous
 * invocations): {@link #call(String, Map)} then reports no attached servers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurCustomToolMcpHelper {

    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurAIAgentRepository agentRepository;
    private final String agentId;
    private final ObjectMapper objectMapper;

    /** Resolved lazily on first {@link #call} so tool setup pays nothing. */
    private ToolCallback[] cachedCallbacks;

    public TurCustomToolMcpHelper(TurMcpToolCallbackService mcpToolCallbackService,
            TurAIAgentRepository agentRepository, String agentId, ObjectMapper objectMapper) {
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.agentRepository = agentRepository;
        this.agentId = agentId;
        this.objectMapper = objectMapper;
    }

    /** Convenience: call an MCP tool with no arguments. */
    public String call(String toolName) {
        return call(toolName, Map.of());
    }

    /**
     * Invokes the named tool of one of the agent's attached MCP servers.
     *
     * @param toolName the MCP tool name (e.g. {@code "dspace_search_items"})
     * @param args     arguments serialized to the tool's JSON input ({@code null}
     *                 → {@code {}})
     * @return the tool's raw result string, or a {@code {"error": "..."}} JSON
     *         string on a missing name / unknown tool / invocation failure
     */
    public String call(String toolName, Map<String, ?> args) {
        if (toolName == null || toolName.isBlank()) {
            return error("mcp.call requires a non-blank tool name");
        }
        ToolCallback[] callbacks = resolveCallbacks();
        if (callbacks.length == 0) {
            return error("no MCP servers are attached to this agent (or no agent context)");
        }
        ToolCallback target = null;
        for (ToolCallback cb : callbacks) {
            if (toolName.equals(safeName(cb))) {
                target = cb;
                break;
            }
        }
        if (target == null) {
            return error("MCP tool '" + toolName + "' not found among the agent's attached MCP servers");
        }
        try {
            String inputJson = objectMapper.writeValueAsString(args == null ? Map.of() : args);
            return target.call(inputJson);
        } catch (Exception e) {
            log.warn("[CustomTool.mcp] call to '{}' failed: {}", toolName, e.getMessage());
            return error("MCP tool '" + toolName + "' failed: " + e.getMessage());
        }
    }

    /** Lazily loads the attached servers' tool callbacks once per helper instance. */
    private ToolCallback[] resolveCallbacks() {
        if (cachedCallbacks != null) {
            return cachedCallbacks;
        }
        if (agentId == null || agentId.isBlank()) {
            cachedCallbacks = new ToolCallback[0];
            return cachedCallbacks;
        }
        cachedCallbacks = agentRepository.findById(agentId)
                .map(TurAIAgent::getMcpServers)
                .filter(servers -> servers != null && !servers.isEmpty())
                .map(this::callbacksFor)
                .orElseGet(() -> new ToolCallback[0]);
        return cachedCallbacks;
    }

    private ToolCallback[] callbacksFor(Set<TurMcpServer> servers) {
        try {
            return mcpToolCallbackService.getToolCallbacks(servers);
        } catch (RuntimeException e) {
            log.warn("[CustomTool.mcp] could not load MCP tool callbacks: {}", e.getMessage());
            return new ToolCallback[0];
        }
    }

    private static String safeName(ToolCallback cb) {
        try {
            return cb.getToolDefinition().name();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}
