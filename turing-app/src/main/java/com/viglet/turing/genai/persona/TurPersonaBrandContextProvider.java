/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Exposes the tool callbacks of the MCP server attached to a persona as
 * its "brand context" provider. The tools then ride into the chat
 * executor's {@code ToolCallback[]} alongside the agent-level MCP tools,
 * so the LLM can pull brand manuals (or any other resource the MCP
 * exposes) on demand.
 *
 * <p>If the persona has no brand-context MCP, an empty array is returned
 * — the call site treats that as "no extra tools".
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurPersonaBrandContextProvider {

    private final TurMcpToolCallbackService mcpToolCallbackService;

    public TurPersonaBrandContextProvider(TurMcpToolCallbackService mcpToolCallbackService) {
        this.mcpToolCallbackService = mcpToolCallbackService;
    }

    public ToolCallback[] getToolCallbacks(TurPersona persona) {
        if (persona == null) {
            return new ToolCallback[0];
        }
        TurMcpServer mcpServer = persona.getBrandContextMcpServer();
        if (mcpServer == null) {
            return new ToolCallback[0];
        }
        return mcpToolCallbackService.getToolCallbacks(Set.of(mcpServer));
    }
}
