/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;

/**
 * Read model for a {@link TurChatHumanApproval} (T119). The token is included
 * only on records surfaced through the token endpoint / admin list so the UI
 * can render the resolve action; the {@code promptText} is the human-readable
 * approval request.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurHumanApprovalDto(
        String token,
        String conversationId,
        String nodeId,
        String flowId,
        String approvalSlot,
        String channel,
        String promptText,
        String status,
        String decision,
        String createdAt,
        String expiresAt,
        String decidedAt) {

    public static TurHumanApprovalDto of(TurChatHumanApproval a) {
        return new TurHumanApprovalDto(
                a.getResumeToken(),
                a.getConversationId(),
                a.getNodeId(),
                a.getFlowId(),
                a.getApprovalSlot(),
                a.getChannel(),
                a.getPromptText(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getDecision(),
                a.getCreatedAt() == null ? null : a.getCreatedAt().toString(),
                a.getExpiresAt() == null ? null : a.getExpiresAt().toString(),
                a.getDecidedAt() == null ? null : a.getDecidedAt().toString());
    }
}
