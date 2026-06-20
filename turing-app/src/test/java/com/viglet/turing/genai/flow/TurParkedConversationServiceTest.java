/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurParkedConversationDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for T122 — the parked-conversations read model and unblock action.
 * The flow-graph parse is delegated to {@link TurChatFlowEngineService} (mocked
 * here so the test only exercises the scan / DTO-mapping logic), so the graphs
 * are pre-built from JSON via the same {@code tools.jackson} mapper the engine
 * uses at runtime.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurParkedConversationServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TurChatFlowRepository flowRepository;
    @Mock
    private TurChatFlowStateRepository stateRepository;
    @Mock
    private TurChatFlowEngineService engineService;
    @Mock
    private TurAIAgentRepository agentRepository;

    @InjectMocks
    private TurParkedConversationService service;

    // Pinned "now" so waiting-time computations are deterministic; the parked
    // states are aged relative to this same instant.
    private static final LocalDateTime FIXED_NOW = LocalDateTime.parse("2026-06-15T12:00:00");

    @BeforeEach
    void pinClock() {
        service.setClockForTest(Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static ChatFlowGraph graph(String json) {
        return MAPPER.readValue(json, ChatFlowGraph.class);
    }

    private static TurChatFlow flow(String id, String name) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setName(name);
        return flow;
    }

    private static TurChatFlowState state(String convId, String flowId, String nodeId,
            LocalDateTime updatedAt) {
        TurChatFlow flow = flow(flowId, flowId);
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId(convId);
        state.setFlow(flow);
        state.setCurrentNodeId(nodeId);
        state.setUpdatedAt(updatedAt);
        return state;
    }

    @Test
    void listParked_collectsOnlySuspendStates_resolvesReasonAndAgent_sortsByWaitDesc() {
        TurChatFlow withSuspend = flow("flow-1", "Approval Flow");
        TurChatFlow noSuspend = flow("flow-2", "Plain Flow");
        when(flowRepository.findAll()).thenReturn(List.of(withSuspend, noSuspend));

        when(engineService.parseGraph(withSuspend)).thenReturn(Optional.of(graph(
                "{\"nodes\":[{\"id\":\"s1\",\"type\":\"suspend\",\"data\":{\"label\":\"waiting_approval\"}},"
                        + "{\"id\":\"q1\",\"type\":\"aiQuestion\",\"data\":{}}],\"edges\":[]}")));
        when(engineService.parseGraph(noSuspend)).thenReturn(Optional.of(graph(
                "{\"nodes\":[{\"id\":\"q9\",\"type\":\"aiQuestion\",\"data\":{}}],\"edges\":[]}")));

        // Two conversations parked on the suspend node, with different wait times.
        TurChatFlowState shortWait = state("conv-short", "flow-1", "s1",
                FIXED_NOW.minusMinutes(2));
        TurChatFlowState longWait = state("conv-long", "flow-1", "s1",
                FIXED_NOW.minusHours(3));
        when(stateRepository.findByFlow_IdAndCurrentNodeIdIn(eq("flow-1"), any()))
                .thenReturn(List.of(shortWait, longWait));

        when(flowRepository.findAgentIdByFlowId("flow-1")).thenReturn(Optional.of("agent-1"));
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("My Agent");
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        List<TurParkedConversationDto> result = service.listParked();

        assertThat(result).hasSize(2);
        // Longest wait first.
        assertThat(result.get(0).conversationId()).isEqualTo("conv-long");
        assertThat(result.get(1).conversationId()).isEqualTo("conv-short");
        assertThat(result).allSatisfy(row -> {
            assertThat(row.flowId()).isEqualTo("flow-1");
            assertThat(row.flowName()).isEqualTo("Approval Flow");
            assertThat(row.nodeId()).isEqualTo("s1");
            assertThat(row.reason()).isEqualTo("waiting_approval");
            assertThat(row.agentId()).isEqualTo("agent-1");
            assertThat(row.agentTitle()).isEqualTo("My Agent");
            assertThat(row.since()).isNotBlank();
        });
        assertThat(result.get(0).waitingSeconds()).isGreaterThan(result.get(1).waitingSeconds());
        // The flow with no suspend node never triggers a state lookup.
        verify(stateRepository).findByFlow_IdAndCurrentNodeIdIn(eq("flow-1"), any());
    }

    @Test
    void listParked_unlabeledSuspendNode_fallsBackToSuspendedReason() {
        TurChatFlow flow = flow("flow-1", "Flow");
        when(flowRepository.findAll()).thenReturn(List.of(flow));
        when(engineService.parseGraph(flow)).thenReturn(Optional.of(graph(
                "{\"nodes\":[{\"id\":\"s1\",\"type\":\"suspend\",\"data\":{}}],\"edges\":[]}")));
        when(stateRepository.findByFlow_IdAndCurrentNodeIdIn(eq("flow-1"), any()))
                .thenReturn(List.of(state("conv-1", "flow-1", "s1", FIXED_NOW)));
        when(flowRepository.findAgentIdByFlowId("flow-1")).thenReturn(Optional.empty());

        List<TurParkedConversationDto> result = service.listParked();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).isEqualTo("suspended");
        assertThat(result.get(0).agentId()).isNull();
        assertThat(result.get(0).agentTitle()).isNull();
    }

    @Test
    void listParked_skipsFlowsWithNoSuspendNode_andUnparseableGraphs() {
        TurChatFlow plain = flow("flow-2", "Plain");
        TurChatFlow broken = flow("flow-3", "Broken");
        when(flowRepository.findAll()).thenReturn(List.of(plain, broken));
        when(engineService.parseGraph(plain)).thenReturn(Optional.of(graph(
                "{\"nodes\":[{\"id\":\"q1\",\"type\":\"aiQuestion\",\"data\":{}}],\"edges\":[]}")));
        when(engineService.parseGraph(broken)).thenReturn(Optional.empty());

        assertThat(service.listParked()).isEmpty();
    }

    @Test
    void unblock_delegatesToEngineResumeWithAdminReason() {
        when(engineService.resumeSuspendedFlow("conv-1", null, "admin-unblock"))
                .thenReturn(new TurChatFlowEngineService.ResumeResult(2, false));

        TurChatFlowEngineService.ResumeResult result = service.unblock("conv-1");

        assertThat(result.resumed()).isEqualTo(2);
        assertThat(result.nothingToResume()).isFalse();
        verify(engineService).resumeSuspendedFlow("conv-1", null, "admin-unblock");
    }

    @Test
    void unblockIfPresent_blankId_returnsEmpty_withoutTouchingEngine() {
        lenient().when(engineService.resumeSuspendedFlow(any(), any(), any()))
                .thenReturn(new TurChatFlowEngineService.ResumeResult(0, true));

        assertThat(service.unblockIfPresent("  ")).isEmpty();
        assertThat(service.unblockIfPresent(null)).isEmpty();
    }
}
