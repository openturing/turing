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
package com.viglet.turing.genai.nativeapi.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * T145 / §X.5.b — the cross-vendor MCP federation directory.
 *
 * <p>Keeps a <em>single</em> per-agent allow-list of remote MCP servers (the
 * agent's existing {@code mcpServers} relation on {@link TurAIAgent}) and, when
 * the agent opts in via {@code mcpNativeFederation}, hands the matching subset to
 * whichever native vendor handles the turn. The
 * {@link com.viglet.turing.genai.nativeapi.TurNativeChatExecutor} appends the
 * returned synthetic {@link EnabledCapability} entries to the turn's capability
 * list, so the vendor service wires each one through its already-shipped remote
 * MCP path with no signature change:
 * <ul>
 *   <li>OpenAI → {@code OPENAI_MCP} → the Responses remote {@code mcp} tool (T138)</li>
 *   <li>Anthropic → {@code ANTHROPIC_MCP} → the Messages {@code mcp_servers} connector (T144)</li>
 * </ul>
 *
 * <p>Only {@link TurMcpServerConnectionType#HTTP} servers are federated — a
 * remote vendor can only call a server by URL, so {@code COMMAND} (stdio)
 * servers stay on Turing's in-process MCP client. To avoid double-wiring, the
 * executor suppresses the client callbacks for exactly the servers federated
 * this turn (see {@link #federate}'s {@code serverId}); {@code COMMAND} servers
 * are kept as client tools.
 *
 * <p>Default-off ({@code mcpNativeFederation == false}) returns an empty list,
 * so existing agents are byte-for-byte unaffected.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurMcpFederationDirectory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One federated server: the originating {@code serverId} (so the executor can
     * exclude it from the Turing MCP client path) plus the synthetic
     * vendor-specific {@link EnabledCapability} the native service wires.
     */
    public record FederatedMcp(String serverId, EnabledCapability capability) {
    }

    /**
     * Resolve the agent's federated MCP servers for the vendor that will handle
     * the turn. Empty when the agent hasn't opted in, has no MCP servers, or the
     * vendor has no remote-MCP capability.
     *
     * @param pluginType the lowercase vendor plugin type (e.g. {@code openai}, {@code anthropic})
     */
    public List<FederatedMcp> federate(TurAIAgent agent, String pluginType) {
        if (agent == null || !agent.isMcpNativeFederation() || !StringUtils.hasText(pluginType)) {
            return List.of();
        }
        TurNativeCapability capability = capabilityForVendor(pluginType);
        if (capability == null) {
            return List.of();
        }
        if (agent.getMcpServers() == null || agent.getMcpServers().isEmpty()) {
            return List.of();
        }
        List<FederatedMcp> result = new ArrayList<>();
        for (TurMcpServer server : agent.getMcpServers()) {
            toFederated(server, capability).ifPresent(result::add);
        }
        if (!result.isEmpty()) {
            log.info("[McpFederation] agent '{}' federating {} MCP server(s) to vendor '{}'",
                    agent.getTitle(), result.size(), pluginType);
        }
        return result;
    }

    private Optional<FederatedMcp> toFederated(TurMcpServer server, TurNativeCapability capability) {
        if (server == null || server.getEnabled() != 1
                || server.getConnectionType() != TurMcpServerConnectionType.HTTP
                || !StringUtils.hasText(server.getUrl())) {
            return Optional.empty();
        }
        String name = slugify(server.getTitle(), server.getId());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("serverUrl", server.getUrl());
        // Both keys so either vendor's config reader finds the server name:
        // OpenAI reads serverLabel, Anthropic reads serverName/serverLabel.
        config.put("serverLabel", name);
        config.put("serverName", name);
        return Optional.of(new FederatedMcp(server.getId(),
                new EnabledCapability(capability, MAPPER.writeValueAsString(config))));
    }

    private static TurNativeCapability capabilityForVendor(String pluginType) {
        return switch (pluginType.toLowerCase(Locale.ROOT)) {
            case "openai" -> TurNativeCapability.OPENAI_MCP;
            case "anthropic" -> TurNativeCapability.ANTHROPIC_MCP;
            default -> null;
        };
    }

    /**
     * Slugify a server title into a vendor-safe server label/name
     * ({@code [a-zA-Z0-9_-]}), falling back to {@code mcp_<id-prefix>} when the
     * title has no usable characters. Both OpenAI's {@code server_label} and
     * Anthropic's MCP server {@code name} reject arbitrary punctuation/spaces.
     */
    static String slugify(String title, String id) {
        String slug = title == null ? "" : title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!slug.isEmpty()) {
            return slug;
        }
        String idPart = id == null ? "" : id.replaceAll("[^a-zA-Z0-9]", "");
        return "mcp_" + (idPart.isEmpty() ? "server" : idPart.substring(0, Math.min(8, idPart.length())));
    }
}
