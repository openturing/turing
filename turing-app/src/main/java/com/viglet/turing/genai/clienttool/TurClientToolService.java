/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T438 / §XXII.3 — resolves an agent's declared frontend ("client") tools from
 * its {@code clientToolsJson} column and turns them into {@link ToolCallback}s
 * (advertised to the model) and a name set (used by the loop to detect a client
 * call and by the resume endpoint to reject unknown/disallowed names).
 *
 * <p>The declaration is a JSON array:
 * {@code [{"name":"…","description":"…","schema":{…}}]}. {@code schema} may be a
 * nested JSON-Schema object (kept verbatim) or a string. Malformed entries are
 * skipped (logged) so one bad declaration can't break the turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurClientToolService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<TurBuiltInClientToolProvider> builtInProviders;

    public TurClientToolService(List<TurBuiltInClientToolProvider> builtInProviders) {
        this.builtInProviders = builtInProviders;
    }

    /** True when the agent advertises at least one client tool (custom or built-in). */
    public boolean isEnabled(TurAIAgent agent) {
        return agent != null && !declarations(agent).isEmpty();
    }

    /**
     * Parsed, validated client-tool declarations for the agent (never null).
     *
     * <p>Built-in tools (each from a {@link TurBuiltInClientToolProvider} gated by
     * its own per-agent flag — answer-as-app T442, co-browse T443) are prepended
     * when the agent opts in — independent of {@code clientToolsEnabled} — followed
     * by the operator-declared tools from {@code clientToolsJson}. Names are
     * de-duplicated (first wins), so an operator can override a built-in by
     * declaring a custom tool of the same name.
     */
    public List<TurClientTool> declarations(TurAIAgent agent) {
        if (agent == null) {
            return List.of();
        }
        List<TurClientTool> tools = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // Built-in client tools, each gated by its own per-agent flag (opt-in,
        // standalone — they do not require clientToolsEnabled).
        for (TurBuiltInClientToolProvider provider : builtInProviders) {
            if (!provider.appliesTo(agent)) {
                continue;
            }
            for (TurClientTool builtIn : provider.declarations(agent)) {
                if (seen.add(builtIn.name())) {
                    tools.add(builtIn);
                }
            }
        }
        // Operator-declared client tools (T438).
        String json = agent.getClientToolsJson();
        if (agent.isClientToolsEnabled() && json != null && !json.isBlank()) {
            parseDeclarations(agent, json, tools, seen);
        }
        return tools;
    }

    private void parseDeclarations(TurAIAgent agent, String json, List<TurClientTool> tools,
            Set<String> seen) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isArray()) {
                log.warn("[ClientTool] agent '{}' clientToolsJson is not a JSON array — ignoring",
                        agent.getId());
                return;
            }
            for (JsonNode node : root) {
                String name = text(node, "name");
                if (name == null || name.isBlank() || !seen.add(name)) {
                    continue; // skip nameless / duplicate declarations
                }
                String description = text(node, "description");
                tools.add(new TurClientTool(name.trim(), description, schemaOf(node.get("schema"))));
            }
        } catch (RuntimeException e) {
            log.warn("[ClientTool] agent '{}' clientToolsJson parse failed: {}",
                    agent.getId(), e.getMessage());
        }
    }

    /** A declared schema kept verbatim: a string as-is, an object stringified, else null. */
    private static String schemaOf(JsonNode schemaNode) {
        if (schemaNode == null || schemaNode.isNull()) {
            return null;
        }
        return schemaNode.isString() ? schemaNode.asString() : schemaNode.toString();
    }

    /** Tool callbacks advertising the agent's client tools to the model. */
    public ToolCallback[] buildToolCallbacks(TurAIAgent agent) {
        List<TurClientTool> tools = declarations(agent);
        ToolCallback[] callbacks = new ToolCallback[tools.size()];
        for (int i = 0; i < tools.size(); i++) {
            callbacks[i] = new TurClientToolCallback(tools.get(i));
        }
        return callbacks;
    }

    /** The set of declared client-tool names (used to detect a park + validate resume). */
    public Set<String> namesFor(TurAIAgent agent) {
        Set<String> names = new LinkedHashSet<>();
        for (TurClientTool tool : declarations(agent)) {
            names.add(tool.name());
        }
        return names;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
