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

import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

import lombok.extern.slf4j.Slf4j;

/**
 * T119 / §IX.5.a — runtime for the {@code humanApproval} chat-flow node. Thin
 * adapter the engine calls from {@code walkTransparentNodes}; delegates the
 * persistence + notification to {@link TurHumanApprovalService} so the service
 * stays engine-independent (no circular bean graph).
 *
 * <p><b>Lifecycle on a single {@code humanApproval} node</b>:
 * <ol>
 *   <li><b>First entry</b> — the approval slot is empty. The service raises a
 *       {@code PENDING} record and fires the notification; the engine parks the
 *       conversation here ({@link #onEnter} returns {@code false}).</li>
 *   <li><b>Re-entry while pending</b> — the user sent another turn but no
 *       decision arrived yet. The service no-ops (record already exists) and the
 *       engine parks again.</li>
 *   <li><b>Decision present</b> — the approval slot now holds the operator's
 *       decision (written by {@code resumeSuspendedFlow} from the approval
 *       endpoint or the timeout sweep). {@link #onEnter} returns {@code true}
 *       and the engine advances on the first outgoing edge.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurHumanApprovalNodeExecutor {

    private final TurHumanApprovalService approvalService;

    public TurHumanApprovalNodeExecutor(TurHumanApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * Decides whether the engine should advance past the {@code humanApproval}
     * node. Returns {@code true} when the decision slot is already populated
     * (advance); otherwise raises/refreshes the pending approval and returns
     * {@code false} (park).
     */
    public boolean onEnter(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return true; // nothing to park on — let the engine move on
        }
        String slot = TurHumanApprovalService.effectiveApprovalSlot(node);
        Map<String, String> variables = ChatFlowOps.readVariables(state);
        String decision = variables.get(slot);
        if (decision != null && !decision.isBlank()) {
            log.info("[HumanApproval] node '{}' decision present in slot '{}' on conv '{}' — advancing",
                    node.id(), slot, state.getConversationId());
            return true;
        }
        approvalService.ensurePending(state, node);
        return false;
    }

    /**
     * Marks any still-pending approval for {@code conversationId + nodeId}
     * cancelled — invoked when the engine force-advances past the node (admin
     * unblock) without a recorded decision. No-op otherwise.
     */
    public void cancelPending(String conversationId, String nodeId) {
        approvalService.cancelPending(conversationId, nodeId);
    }
}
