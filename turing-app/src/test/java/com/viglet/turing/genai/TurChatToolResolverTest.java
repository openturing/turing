/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;

/**
 * Tests for T424 / §XXI part (b) — trigger-boost of attached MCP tools in
 * {@link TurChatToolResolver}. Only the MCP collaborator is exercised, so the
 * other constructor dependencies are left null.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatToolResolverTest {

    private final TurMcpToolCallbackService mcpToolCallbackService =
            mock(TurMcpToolCallbackService.class);
    private final TurChatToolResolver resolver = new TurChatToolResolver(
            null, mcpToolCallbackService, null, null, null, null, null, null, null, null);

    private static final String INSPER_INSTRUCTIONS = """
            QUANDO USAR (OBRIGATÓRIO) Sempre que o usuário perguntar sobre a base de
            conhecimento do Insper, acione as tools dspace_.
            Palavras-gatilho: base de conhecimento do insper, acervo do insper, insper,
            dspace, comunidades, coleções, itens, teses, dissertações, metadados.
            """;

    @Test
    void boostsAttachedServerToolsWhenTriggerWordMatches() {
        TurMcpServer server = mcpServer("Insper Repositório", INSPER_INSTRUCTIONS);
        when(mcpToolCallbackService.getToolCallbacks(anySet()))
                .thenReturn(new ToolCallback[] {
                        cb("dspace_search_items"), cb("dspace_list_communities") });

        Set<String> boosted = resolver.triggerBoostedMcpToolNames(
                agentWith(server), "quais são os canais da base de conhecimento do Insper");

        assertThat(boosted).containsExactlyInAnyOrder("dspace_search_items", "dspace_list_communities");
    }

    @Test
    void doesNotBoostWhenNoTriggerWordMatches() {
        TurMcpServer server = mcpServer("Insper Repositório", INSPER_INSTRUCTIONS);

        Set<String> boosted = resolver.triggerBoostedMcpToolNames(
                agentWith(server), "qual a previsão do tempo para amanhã?");

        assertThat(boosted).isEmpty();
    }

    @Test
    void boostsOnServerTitleEvenWithoutTriggerLine() {
        // No "Palavras-gatilho" line — the title token "repositório" still matches.
        TurMcpServer server = mcpServer("Repositório Acadêmico", "Use estas tools para o acervo.");
        when(mcpToolCallbackService.getToolCallbacks(anySet()))
                .thenReturn(new ToolCallback[] { cb("dspace_get_item") });

        Set<String> boosted = resolver.triggerBoostedMcpToolNames(
                agentWith(server), "me mostra o repositório acadêmico");

        assertThat(boosted).containsExactly("dspace_get_item");
    }

    @Test
    void disabledServerIsNotBoosted() {
        TurMcpServer server = mcpServer("Insper Repositório", INSPER_INSTRUCTIONS);
        server.setEnabled(0);

        Set<String> boosted = resolver.triggerBoostedMcpToolNames(
                agentWith(server), "base de conhecimento do insper");

        assertThat(boosted).isEmpty();
    }

    @Test
    void blankMessageOrNoServersYieldsEmpty() {
        assertThat(resolver.triggerBoostedMcpToolNames(agentWith(), "anything")).isEmpty();
        TurMcpServer server = mcpServer("Insper Repositório", INSPER_INSTRUCTIONS);
        assertThat(resolver.triggerBoostedMcpToolNames(agentWith(server), "  ")).isEmpty();
    }

    // ── helpers ──

    private static TurAIAgent agentWith(TurMcpServer... servers) {
        TurAIAgent agent = new TurAIAgent();
        agent.setMcpServers(new LinkedHashSet<>(Set.of(servers)));
        return agent;
    }

    private static TurMcpServer mcpServer(String title, String llmInstructions) {
        TurMcpServer server = new TurMcpServer();
        server.setTitle(title);
        server.setLlmInstructions(llmInstructions);
        server.setEnabled(1);
        return server;
    }

    private static ToolCallback cb(String name) {
        ToolDefinition def = DefaultToolDefinition.builder()
                .name(name).description(".").inputSchema("{}").build();
        ToolMetadata metadata = DefaultToolMetadata.builder().build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return def;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String toolInput) {
                return "";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "";
            }
        };
    }
}
