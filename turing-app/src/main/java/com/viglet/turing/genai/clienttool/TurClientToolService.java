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

    /** True when the agent opts in and declares at least one valid client tool. */
    public boolean isEnabled(TurAIAgent agent) {
        return agent != null && agent.isClientToolsEnabled() && !declarations(agent).isEmpty();
    }

    /** Parsed, validated client-tool declarations for the agent (never null). */
    public List<TurClientTool> declarations(TurAIAgent agent) {
        if (agent == null || !agent.isClientToolsEnabled()) {
            return List.of();
        }
        String json = agent.getClientToolsJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<TurClientTool> tools = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isArray()) {
                log.warn("[ClientTool] agent '{}' clientToolsJson is not a JSON array — ignoring",
                        agent.getId());
                return List.of();
            }
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode node : root) {
                String name = text(node, "name");
                if (name == null || name.isBlank() || !seen.add(name)) {
                    continue; // skip nameless / duplicate declarations
                }
                String description = text(node, "description");
                JsonNode schemaNode = node.get("schema");
                String schema = schemaNode == null || schemaNode.isNull()
                        ? null
                        : (schemaNode.isTextual() ? schemaNode.asString() : schemaNode.toString());
                tools.add(new TurClientTool(name.trim(), description, schema));
            }
        } catch (RuntimeException e) {
            log.warn("[ClientTool] agent '{}' clientToolsJson parse failed: {}",
                    agent.getId(), e.getMessage());
            return List.of();
        }
        return tools;
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
