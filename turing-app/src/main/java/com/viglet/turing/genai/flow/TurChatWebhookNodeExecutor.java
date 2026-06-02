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

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.service.chatslots.TurChatWebhookService;
import com.viglet.turing.service.chatslots.TurChatWebhookService.DispatchOutcome;

import lombok.extern.slf4j.Slf4j;

/**
 * T62 — runtime execution for the {@code webhook} chat-flow node type. Fires a
 * named, admin-declared {@link com.viglet.turing.persistence.model.agent.TurChatWebhook}
 * deterministically at a precise step of the flow (a CRM push the author wants
 * to guarantee), rather than waiting on a slot-write trigger or an explicit
 * handoff click.
 *
 * <p><b>Schema</b> (reuses existing {@link ChatFlowNode} fields, no new
 * data-model columns — same approach as {@code functionCall}):
 * <ul>
 *   <li>{@code functionName} — the webhook name to fire (the catalog entry's
 *       unique name, picked from a dropdown in the editor).</li>
 *   <li>{@code continueOnFailure} — when {@code true}, a delivery failure
 *       routes the flow along the {@code "failure"} outgoing edge (T49/T50);
 *       otherwise the engine logs + advances to the first edge.</li>
 * </ul>
 *
 * <p>The webhook's own configuration (target URL, HTTP method, headers,
 * payload template, auth, slot whitelist) lives on the entity — the node only
 * names which webhook to fire. The full conversation slot map is passed so the
 * webhook's template/whitelist can shape the payload.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatWebhookNodeExecutor {

    private final TurChatWebhookService webhookService;

    public TurChatWebhookNodeExecutor(TurChatWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Fires the webhook named by {@code node.functionName()} with the
     * conversation's current slots. Pure side-effect (no slot written); the
     * caller advances the cursor based on {@link ExecutionResult#ok()}.
     */
    public ExecutionResult execute(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return ExecutionResult.failure("state or node is null");
        }
        String webhookName = node.functionName();
        if (webhookName == null || webhookName.isBlank()) {
            return ExecutionResult.failure("webhook node '" + node.id()
                    + "' has no webhook selected (functionName) — skipping");
        }
        DispatchOutcome outcome = webhookService.dispatchFromFlowNode(
                webhookName, state.getConversationId(), ChatFlowOps.readVariables(state));
        if (!outcome.ok()) {
            log.warn("[FlowOps/webhook] node '{}' webhook '{}' failed on conv '{}': {}",
                    node.id(), webhookName, state.getConversationId(), outcome.error());
            return ExecutionResult.failure(outcome.error());
        }
        log.info("[FlowOps/webhook] node '{}' fired webhook '{}' on conv '{}'",
                node.id(), webhookName, state.getConversationId());
        return ExecutionResult.success();
    }

    /**
     * Outcome of an {@link #execute(TurChatFlowState, ChatFlowNode)} call.
     * {@code ok=false} ⇒ unresolved/disabled webhook or a delivery failure;
     * {@code error} carries a one-line reason for the warning log + failure
     * edge routing.
     */
    public record ExecutionResult(boolean ok, String error) {
        static ExecutionResult success() {
            return new ExecutionResult(true, null);
        }

        static ExecutionResult failure(String error) {
            return new ExecutionResult(false, error);
        }
    }
}
