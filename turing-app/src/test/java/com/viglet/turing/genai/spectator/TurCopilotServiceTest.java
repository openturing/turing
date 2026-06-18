/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.spectator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import reactor.core.Disposable;

/**
 * Unit coverage for the T120 co-pilot manual-turn path and the spectator
 * message bus. Exercises the pure-interjection path (no flow advance) so the
 * test stays free of the flow engine's graph machinery while still asserting
 * the contract that matters: telemetry records, the assistant event is teed
 * onto the bus flagged {@code manual=true}, and validation rejects a blank
 * reply.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCopilotServiceTest {

    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurChatFlowRepository chatFlowRepository;
    @Mock private TurChatFlowEngineService chatFlowEngineService;
    @Mock private TurChatMemoryService chatMemoryService;
    @Mock private TurChatAnalyticsService chatAnalyticsService;
    @Mock private TurLlmModelFactory llmModelFactory;
    @Mock private TurSecretCryptoService turSecretCryptoService;

    private TurCopilotService newService(TurChatMessageEventBus bus) {
        return new TurCopilotService(agentRepository, chatFlowRepository, chatFlowEngineService,
                chatMemoryService, chatAnalyticsService, bus, llmModelFactory, turSecretCryptoService);
    }

    @Test
    void manualTurnInterjectionRecordsTelemetryAndPublishesManualAssistant() {
        TurChatMessageEventBus bus = new TurChatMessageEventBus();
        TurCopilotService service = newService(bus);

        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(chatAnalyticsService.isEnabled()).thenReturn(true);

        // Subscribe BEFORE the turn so the hot bus delivers the operator reply.
        List<TurChatMessageEvent> received = new CopyOnWriteArrayList<>();
        Disposable sub = bus.subscribe("conv-1").subscribe(received::add);

        // No userMessage => pure interjection, the flow is never advanced.
        TurCopilotService.ManualTurnResult result = service.manualTurn(
                "conv-1", "agent-1", null, null, "Hi, this is a human operator.");
        sub.dispose();

        assertTrue(result.ok());
        assertEquals("Hi, this is a human operator.", result.assistantMessage());
        assertTrue(result.suggestedOptions().isEmpty());

        // Flow engine untouched on a pure interjection.
        verify(chatFlowEngineService, never()).advance(any(), any(), any(), any(), any(), any());
        // Chat memory + analytics still record the turn.
        verify(chatMemoryService).recordTurn(null, agent, "conv-1", null, null,
                "Hi, this is a human operator.");
        verify(chatAnalyticsService).recordTurn("conv-1", 0L, 0L);

        // Exactly one event reached the bus: the operator's assistant reply,
        // flagged manual=true (no user event because userMessage was null).
        assertEquals(1, received.size());
        TurChatMessageEvent event = received.get(0);
        assertEquals(TurChatMessageEvent.ROLE_ASSISTANT, event.role());
        assertTrue(event.manual());
        assertEquals("Hi, this is a human operator.", event.content());
    }

    @Test
    void manualTurnRejectsBlankAssistantMessage() {
        TurCopilotService service = newService(new TurChatMessageEventBus());

        TurCopilotService.ManualTurnResult result =
                service.manualTurn("conv-1", "agent-1", null, "anything", "   ");

        assertFalse(result.ok());
        assertEquals("assistantMessage is required", result.error());
        // Never even touches the agent repo when the reply is blank.
        verify(agentRepository, never()).findById(any());
    }

    @Test
    void manualTurnFailsWhenAgentMissing() {
        TurCopilotService service = newService(new TurChatMessageEventBus());
        when(agentRepository.findById("missing")).thenReturn(Optional.empty());

        TurCopilotService.ManualTurnResult result =
                service.manualTurn("conv-1", "missing", null, null, "operator reply");

        assertFalse(result.ok());
        assertTrue(result.error().contains("AI Agent not found"));
    }

    @Test
    void messageBusFiltersByConversationAndDropsBlankContent() {
        TurChatMessageEventBus bus = new TurChatMessageEventBus();

        List<TurChatMessageEvent> convA = new CopyOnWriteArrayList<>();
        List<TurChatMessageEvent> convB = new CopyOnWriteArrayList<>();
        Disposable subA = bus.subscribe("A").subscribe(convA::add);
        Disposable subB = bus.subscribe("B").subscribe(convB::add);

        bus.publish(TurChatMessageEvent.user("A", "hello from A"));
        bus.publish(TurChatMessageEvent.assistant("B", "hello from B"));
        bus.publish(TurChatMessageEvent.assistant("A", "  ")); // blank -> dropped
        bus.publish(null);                                      // null -> dropped

        subA.dispose();
        subB.dispose();

        assertEquals(1, convA.size());
        assertEquals("hello from A", convA.get(0).content());
        assertEquals(1, convB.size());
        assertEquals("hello from B", convB.get(0).content());
    }
}
