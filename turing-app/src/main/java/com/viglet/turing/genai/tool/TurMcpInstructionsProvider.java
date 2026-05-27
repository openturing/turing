/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.genai.tool;

import java.util.Comparator;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.mcp.TurMcpServer;

/**
 * Builds the system-prompt fragment that carries each attached MCP server's
 * {@code llmInstructions} into the agent prompt. Only servers that are
 * <em>enabled</em> (matching the tool-callback selection in
 * {@link TurMcpToolCallbackService}) and that carry non-blank instructions
 * contribute — so a server can expose tools without forcing prompt text, or
 * carry guidance text and be toggled off without leaking into the prompt.
 *
 * <p>The block is appended to the agent base prompt by
 * {@link com.viglet.turing.genai.TurChatPromptAssembler} before the flow
 * addendum and persona composition.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurMcpInstructionsProvider {

    /** Markdown heading that opens the injected block. */
    static final String BLOCK_HEADER = "# MCP server instructions";

    /**
     * Compose the system-prompt block for the supplied MCP servers.
     *
     * @param mcpServers the agent's attached MCP servers (may be {@code null}
     *        or contain disabled / instruction-less entries — all filtered out)
     * @return a leading-newline block ready to append to the base prompt, or
     *         an empty string when no server contributes instructions
     */
    public String buildSystemPromptBlock(Set<TurMcpServer> mcpServers) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return "";
        }
        // Stable order (by title) so the prompt is deterministic across turns
        // — important for prompt caching and reproducible behaviour.
        StringBuilder sb = new StringBuilder();
        mcpServers.stream()
                .filter(TurMcpInstructionsProvider::contributes)
                .sorted(Comparator.comparing(s -> safe(s.getTitle()).toLowerCase()))
                .forEach(server -> sb
                        .append("\n\n## ")
                        .append(safe(server.getTitle()).isBlank() ? "MCP server" : server.getTitle().trim())
                        .append('\n')
                        .append(server.getLlmInstructions().trim()));
        if (sb.isEmpty()) {
            return "";
        }
        return "\n\n" + BLOCK_HEADER
                + "\nWhen deciding whether and how to call the tools exposed by the following"
                + " MCP servers, follow these instructions:"
                + sb;
    }

    private static boolean contributes(TurMcpServer server) {
        return server != null
                && server.getEnabled() == 1
                && server.getLlmInstructions() != null
                && !server.getLlmInstructions().isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
