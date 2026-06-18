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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the T119 {@code humanApproval} node executor: parks (raising a
 * pending approval) when the decision slot is empty, and advances once the slot
 * is filled — without re-raising.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurHumanApprovalNodeExecutorTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String NODE_ID = "ha-1";

    @Test
    @DisplayName("empty decision slot → parks (returns false) and raises the pending approval")
    void emptySlot_parksAndRaises() {
        TurHumanApprovalService service = mock(TurHumanApprovalService.class);
        TurHumanApprovalNodeExecutor executor = new TurHumanApprovalNodeExecutor(service);
        TurChatFlowState state = stateWithSlots(Map.of());

        boolean advance = executor.onEnter(state, node("operator_decision"));

        assertThat(advance).isFalse();
        verify(service).ensurePending(state, node("operator_decision"));
    }

    @Test
    @DisplayName("decision present in slot → advances (returns true) without raising")
    void decisionPresent_advances() {
        TurHumanApprovalService service = mock(TurHumanApprovalService.class);
        TurHumanApprovalNodeExecutor executor = new TurHumanApprovalNodeExecutor(service);
        TurChatFlowState state = stateWithSlots(Map.of("operator_decision", "approve"));

        boolean advance = executor.onEnter(state, node("operator_decision"));

        assertThat(advance).isTrue();
        verify(service, never()).ensurePending(any(), any());
    }

    @Test
    @DisplayName("null state/node → advances (nothing to park on)")
    void nullArgs_advance() {
        TurHumanApprovalNodeExecutor executor =
                new TurHumanApprovalNodeExecutor(mock(TurHumanApprovalService.class));
        assertThat(executor.onEnter(null, node("operator_decision"))).isTrue();
        assertThat(executor.onEnter(stateWithSlots(Map.of()), null)).isTrue();
    }

    // ─────────────────────── helpers ───────────────────────

    private static TurChatFlowState stateWithSlots(Map<String, String> slots) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        ChatFlowOps.writeVariables(state, new LinkedHashMap<>(slots));
        return state;
    }

    private static ChatFlowNode node(String approvalSlot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "Approve");
        data.put("type", "humanApproval");
        data.put("humanApproval", Map.of(
                "channel", "email", "target", "ops@example.com", "approvalSlot", approvalSlot));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", NODE_ID);
        root.put("type", "humanApproval");
        root.put("data", data);
        return OM.convertValue(root, ChatFlowNode.class);
    }
}
