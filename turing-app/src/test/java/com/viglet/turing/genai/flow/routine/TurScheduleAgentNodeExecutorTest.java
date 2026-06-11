/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsMessagingTemplate;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.routine.TurScheduleAgentNodeExecutor.Outcome;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;
import com.viglet.turing.sn.TurSNConstants;

/**
 * Pin tests for the T48 {@code scheduleAgent} runtime. Exercises the
 * three-phase lifecycle (fire → wait → complete) plus the timeout and
 * resolution-failure paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurScheduleAgentNodeExecutorTest {

    private static final String ROUTINE_ID = "r-1";
    private static final String NODE_ID = "sa-1";
    private static final String OUTPUT_SLOT = "proposta_pdf_url";

    @Test
    @DisplayName("first entry: enqueues JMS, writes pending markers, returns WAITING")
    void firstEntry_firesJmsAndParks() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById(ROUTINE_ID)).thenReturn(Optional.of(enabledRoutine()));
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);

        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());
        TurChatFlowState state = stateWithSlots(Map.of("cargo", "CEO"));
        ChatFlowNode node = scheduleAgentNode(ROUTINE_ID, "{\"cargo\":\"{{cargo}}\"}", OUTPUT_SLOT, null);

        Outcome outcome = executor.execute(state, node);

        assertThat(outcome).isEqualTo(Outcome.WAITING_FIRED);
        verify(jms).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE), any(TurScheduleAgentMessage.class), anyMap());
        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsKey("__scheduleAgent_pending_" + NODE_ID);
        assertThat(vars).containsKey("__scheduleAgent_startedAt_" + NODE_ID);
    }

    @Test
    @DisplayName("re-entry with output slot filled → COMPLETED, markers cleared")
    void reEntry_outputSlotFilled_isCompleted() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        TurChatFlowState state = stateWithSlots(Map.of(
                "__scheduleAgent_pending_" + NODE_ID, ROUTINE_ID,
                "__scheduleAgent_startedAt_" + NODE_ID, Long.toString(System.currentTimeMillis()),
                OUTPUT_SLOT, "https://example.com/proposal.pdf"));

        Outcome outcome = executor.execute(state,
                scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, null));

        assertThat(outcome).isEqualTo(Outcome.COMPLETED);
        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).doesNotContainKey("__scheduleAgent_pending_" + NODE_ID);
        assertThat(vars).doesNotContainKey("__scheduleAgent_startedAt_" + NODE_ID);
        assertThat(vars.get(OUTPUT_SLOT)).isEqualTo("https://example.com/proposal.pdf");
        verify(jms, never()).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE),
                any(TurScheduleAgentMessage.class));
    }

    @Test
    @DisplayName("re-entry while routine still running → WAITING (no JMS re-enqueue)")
    void reEntry_stillRunning_isWaiting() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById(ROUTINE_ID)).thenReturn(Optional.of(enabledRoutine()));
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        TurChatFlowState state = stateWithSlots(Map.of(
                "__scheduleAgent_pending_" + NODE_ID, ROUTINE_ID,
                "__scheduleAgent_startedAt_" + NODE_ID, Long.toString(System.currentTimeMillis())));

        Outcome outcome = executor.execute(state,
                scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, null));

        assertThat(outcome).isEqualTo(Outcome.WAITING_POLL);
        verify(jms, never()).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE),
                any(TurScheduleAgentMessage.class));
    }

    @Test
    @DisplayName("re-entry past timeout → TIMEOUT, markers cleared, no re-enqueue")
    void reEntry_pastTimeout_isTimeout() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById(ROUTINE_ID)).thenReturn(Optional.of(enabledRoutine()));
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        long ancient = System.currentTimeMillis() - 999_999L;
        TurChatFlowState state = stateWithSlots(Map.of(
                "__scheduleAgent_pending_" + NODE_ID, ROUTINE_ID,
                "__scheduleAgent_startedAt_" + NODE_ID, Long.toString(ancient)));

        ChatFlowNode node = scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, 5_000);
        Outcome outcome = executor.execute(state, node);

        assertThat(outcome).isEqualTo(Outcome.TIMEOUT);
        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).doesNotContainKey("__scheduleAgent_pending_" + NODE_ID);
        assertThat(vars).doesNotContainKey("__scheduleAgent_startedAt_" + NODE_ID);
    }

    @Test
    @DisplayName("missing routineId → FAILED, no JMS, no markers")
    void missingRoutineId_isFailed() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        Outcome outcome = executor.execute(stateWithSlots(Map.of()),
                scheduleAgentNode(null, null, OUTPUT_SLOT, null));

        assertThat(outcome).isEqualTo(Outcome.FAILED);
        verify(jms, never()).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE),
                any(TurScheduleAgentMessage.class));
    }

    @Test
    @DisplayName("routine not found in DB → FAILED")
    void routineNotFound_isFailed() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById(ROUTINE_ID)).thenReturn(Optional.empty());
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        Outcome outcome = executor.execute(stateWithSlots(Map.of()),
                scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, null));

        assertThat(outcome).isEqualTo(Outcome.FAILED);
        verify(jms, never()).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE),
                any(TurScheduleAgentMessage.class));
    }

    @Test
    @DisplayName("disabled routine → FAILED, no JMS")
    void disabledRoutine_isFailed() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        TurRoutine disabled = enabledRoutine();
        disabled.setEnabled(false);
        when(repo.findById(ROUTINE_ID)).thenReturn(Optional.of(disabled));
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        Outcome outcome = executor.execute(stateWithSlots(Map.of()),
                scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, null));

        assertThat(outcome).isEqualTo(Outcome.FAILED);
        verify(jms, never()).convertAndSend(eq(TurSNConstants.ROUTINE_QUEUE),
                any(TurScheduleAgentMessage.class));
    }

    @Test
    @DisplayName("null state / null node → FAILED without NPE")
    void nullInputs_failGracefully() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        JmsMessagingTemplate jms = mock(JmsMessagingTemplate.class);
        TurScheduleAgentNodeExecutor executor = new TurScheduleAgentNodeExecutor(repo, jms, noTenantPropagation());

        assertThat(executor.execute(null, scheduleAgentNode(ROUTINE_ID, null, OUTPUT_SLOT, null)))
                .isEqualTo(Outcome.FAILED);
        assertThat(executor.execute(stateWithSlots(Map.of()), null))
                .isEqualTo(Outcome.FAILED);
    }

    // ─────────────────────── helpers ───────────────────────

    private static TurChatFlowState stateWithSlots(Map<String, String> slots) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        ChatFlowOps.writeVariables(state, new LinkedHashMap<>(slots));
        return state;
    }

    private static TurRoutine enabledRoutine() {
        TurRoutine r = new TurRoutine();
        r.setId(ROUTINE_ID);
        r.setName("generate_proposal_pdf");
        r.setNativeToolName("generate_proposal_pdf");
        r.setDefaultTimeoutMs(60_000);
        r.setEnabled(true);
        return r;
    }

    private static ChatFlowNode scheduleAgentNode(String routineId, String aiInstruction,
            String outputVariable, Integer timeoutMs) {
        return new ChatFlowNode(NODE_ID, "scheduleAgent", new NodeData(
                "Schedule routine", "scheduleAgent",
                aiInstruction, outputVariable, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(),
                null, null, null, null, null, null, List.of(),
                routineId, timeoutMs));
    }

    private static com.viglet.turing.tenant.TurJmsTenantPropagation noTenantPropagation() {
        com.viglet.turing.properties.TurConfigProperties props =
                new com.viglet.turing.properties.TurConfigProperties();
        return new com.viglet.turing.tenant.TurJmsTenantPropagation(
                new com.viglet.turing.tenant.TurTenantContext(props));
    }
}
