/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link TurMcpAgentToolService} — the T249 {@code list_agents}
 * and {@code invoke_agent} MCP tools.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpAgentToolServiceTest {

    @Mock
    private TurAIAgentRepository turAIAgentRepository;
    @Mock
    private TurAgentChatExecutor agentChatExecutor;
    @Mock
    private TurChatFlowEngineService chatFlowEngineService;

    @InjectMocks
    private TurMcpAgentToolService service;

    private TurAIAgent agent(String id, String title, int enabled, TurLLMInstance llm) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(id);
        agent.setTitle(title);
        agent.setDescription("desc-" + title);
        agent.setEnabled(enabled);
        if (llm != null) {
            agent.setLlmInstances(Set.of(llm));
        } else {
            agent.setLlmInstances(Set.of());
        }
        return agent;
    }

    private TurLLMInstance llm(String id) {
        TurLLMInstance llm = new TurLLMInstance();
        llm.setId(id);
        return llm;
    }

    @Test
    void listAgents_returnsOnlyEnabled() {
        when(turAIAgentRepository.findAll()).thenReturn(List.of(
                agent("a1", "Support Bot", 1, llm("l1")),
                agent("a2", "Draft Bot", 0, llm("l2"))));

        String result = service.listAgents();

        assertTrue(result.contains("Support Bot"), result);
        assertFalse(result.contains("Draft Bot"), result);
        assertTrue(result.contains("a1"), result);
    }

    @Test
    void listAgents_emptyWhenNoneEnabled() {
        when(turAIAgentRepository.findAll()).thenReturn(List.of(agent("a2", "Draft Bot", 0, null)));
        assertTrue(service.listAgents().startsWith("No enabled AI agents"));
    }

    @Test
    void invokeAgent_missingAgentId_returnsError() {
        assertTrue(service.invokeAgent("", "hi", null).startsWith("Error"));
        verify(agentChatExecutor, never()).execute(any(TurAgentChatRequest.class),
                nullable(String.class), nullable(String.class));
    }

    @Test
    void invokeAgent_agentNotFound_returnsError() {
        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());
        assertTrue(service.invokeAgent("missing", "hi", null).contains("not found"));
    }

    @Test
    void invokeAgent_disabledAgent_returnsError() {
        when(turAIAgentRepository.findById("a2")).thenReturn(Optional.of(agent("a2", "Draft", 0, llm("l1"))));
        assertTrue(service.invokeAgent("a2", "hi", null).contains("disabled"));
    }

    @Test
    void invokeAgent_noLlm_returnsError() {
        when(turAIAgentRepository.findById("a3")).thenReturn(Optional.of(agent("a3", "NoLlm", 1, null)));
        assertTrue(service.invokeAgent("a3", "hi", null).contains("no LLM instance"));
    }

    @Test
    void invokeAgent_collectsAnswerSlotsAndConversation() {
        TurAIAgent agent = agent("a1", "Support Bot", 1, llm("l1"));
        when(turAIAgentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(agentChatExecutor.execute(any(TurAgentChatRequest.class),
                nullable(String.class), nullable(String.class)))
                .thenReturn(Flux.just(
                        new ChatResponse("assistant", "Hello ", "token"),
                        new ChatResponse("assistant", "there!", "token"),
                        new ChatResponse("assistant", "[chip]", "options")));
        lenient().when(chatFlowEngineService.listSlotsForConversation(anyString()))
                .thenReturn(new TurChatSessionSlotsDto("conv-1",
                        Map.of("email", "a@b.com")));

        String result = service.invokeAgent("a1", "hi", "conv-1");

        // token parts concatenated; "options" chip excluded.
        assertTrue(result.contains("Hello there!"), result);
        assertFalse(result.contains("[chip]"), result);
        assertTrue(result.contains("Captured slots:"), result);
        assertTrue(result.contains("email: a@b.com"), result);
        assertTrue(result.contains("Conversation: conv-1"), result);
    }
}
