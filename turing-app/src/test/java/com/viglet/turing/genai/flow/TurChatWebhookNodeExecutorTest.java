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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.TurChatWebhookNodeExecutor.ExecutionResult;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.service.chatslots.TurChatWebhookService;
import com.viglet.turing.service.chatslots.TurChatWebhookService.DispatchOutcome;

/**
 * Pin tests for the T62 {@code webhook} chat-flow node runtime. The node only
 * names a webhook ({@code functionName}); the actual POST is delegated to
 * {@link TurChatWebhookService#dispatchFromFlowNode}, mocked here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatWebhookNodeExecutorTest {

    @Test
    void happyPathDispatchesAndReturnsOk() {
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        when(webhookService.dispatchFromFlowNode(eq("push_crm"), eq("conv-1"), any()))
                .thenReturn(new DispatchOutcome(true, "webhook://push_crm", null));

        TurChatWebhookNodeExecutor executor = new TurChatWebhookNodeExecutor(webhookService);
        ExecutionResult result = executor.execute(
                stateWithSlots(java.util.Map.of("email", "ada@x.com")),
                webhookNode("push_crm"));

        assertThat(result.ok()).isTrue();
        verify(webhookService).dispatchFromFlowNode(eq("push_crm"), eq("conv-1"), any());
    }

    @Test
    void deliveryFailurePropagatesAsFailure() {
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        when(webhookService.dispatchFromFlowNode(any(), any(), any()))
                .thenReturn(DispatchOutcome.failure("Webhook delivery failed: timeout"));

        TurChatWebhookNodeExecutor executor = new TurChatWebhookNodeExecutor(webhookService);
        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()), webhookNode("push_crm"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("timeout");
    }

    @Test
    void blankWebhookNameFailsWithoutDispatch() {
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        TurChatWebhookNodeExecutor executor = new TurChatWebhookNodeExecutor(webhookService);

        ExecutionResult result = executor.execute(stateWithSlots(java.util.Map.of()), webhookNode(null));

        assertThat(result.ok()).isFalse();
        verify(webhookService, never()).dispatchFromFlowNode(any(), any(), any());
    }

    @Test
    void nullInputsFailGracefully() {
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        TurChatWebhookNodeExecutor executor = new TurChatWebhookNodeExecutor(webhookService);

        assertThat(executor.execute(null, webhookNode("push_crm")).ok()).isFalse();
        assertThat(executor.execute(stateWithSlots(java.util.Map.of()), null).ok()).isFalse();
        verify(webhookService, never()).dispatchFromFlowNode(any(), any(), any());
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static TurChatFlowState stateWithSlots(java.util.Map<String, String> slots) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-1");
        ChatFlowOps.writeVariables(state, new java.util.LinkedHashMap<>(slots));
        return state;
    }

    /** A {@code webhook} node carrying the webhook name in {@code functionName}. */
    private static ChatFlowNode webhookNode(String webhookName) {
        NodeData data = new NodeData("WEBHOOK", "webhook", null, null, null, webhookName,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        return new ChatFlowNode("n1", "webhook", data);
    }
}
