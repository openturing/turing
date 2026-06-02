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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * T65 / §VII.6.f — full snapshot export of a chat conversation's runtime
 * state, returned by {@code GET /api/chat/sessions/{conversationId}/export}.
 *
 * <p>Bundles everything a debugger / regression harness needs to reconstruct
 * what happened in a conversation, in one JSON document:
 * <ul>
 *   <li>{@link #flowStates} — the raw {@code chat_flow_state} rows (one per
 *       active flow / sub-flow), each with its parsed variable map and cursor
 *       node, so the exact engine position is reproducible.</li>
 *   <li>{@link #submissions} — finished flow runs read from the append-only
 *       {@code chat_flow_submission} table.</li>
 *   <li>{@link #slots} — the conversation-scoped slot map merged across every
 *       state and submission (same view the SDK / SSE channel sees).</li>
 *   <li>{@link #transcript} — chat-memory messages (MongoDB / Redis), subject
 *       to the chosen {@code limit}; {@code transcriptEngine} +
 *       {@code transcriptEnabled} report the backing store.</li>
 *   <li>{@link #slotAudit} — the T60 slot-write timeline (origin + old → new
 *       per write), {@code pii_*} values already redacted at persist time.</li>
 * </ul>
 *
 * <p>{@link #exportedAt} stamps when the snapshot was taken so two exports of
 * the same conversation can be diffed in order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSessionExportDto(
        String conversationId,
        LocalDateTime exportedAt,
        List<FlowState> flowStates,
        List<TurChatFlowSubmissionDto> submissions,
        Map<String, String> slots,
        String transcriptEngine,
        boolean transcriptEnabled,
        List<TurChatSessionMessageDto> transcript,
        List<TurChatSlotAuditDto.Entry> slotAudit) {

    /**
     * One raw {@code chat_flow_state} row, flattened for export. {@code variables}
     * is the parsed {@code variablesJson} (slot snapshot for this flow); PII
     * values remain in their encrypted-at-rest envelope form exactly as stored.
     */
    public record FlowState(
            String stateId,
            String flowId,
            String flowName,
            String currentNodeId,
            String parentStateId,
            Map<String, String> variables,
            LocalDateTime updatedAt) {
    }
}
