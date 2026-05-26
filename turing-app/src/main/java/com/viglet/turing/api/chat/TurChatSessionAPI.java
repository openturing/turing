/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.chat;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.dto.agent.TurChatSlotAuditDto;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.chatslots.TurChatSlotAuditService;
import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cross-agent lookup of variables collected during a single chat session.
 *
 * <p>The path's {@code conversationId} must be the exact value persisted by
 * the React SDK in the {@code TUR_SESSION} cookie (configurable via
 * {@code turing.chat.session.cookie-name}). That same string is used by the
 * engine as the row key in {@code chat_flow_state} and
 * {@code chat_flow_submission}, so a single call returns every flow that
 * ran (or is still running) for the given visitor.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@RestController
@RequestMapping("/api/chat/sessions")
@Tag(name = "Chat Session", description = "Chat session inspection API")
public class TurChatSessionAPI {

    private static final String MEMORY_SUMMARY_INSTRUCTIONS = """
            You are summarising a chat-memory transcript between a visitor and an AI assistant. \
            Produce a concise Markdown summary that captures: \

            ## Visitor intent
            What the user came in for and what they actually asked.

            ## Key facts captured
            Bullet list of concrete data points the visitor disclosed (name, email, course, \
            preferences, etc.) — these are the values most likely worth persisting as long-term memory.

            ## Outcome
            How the conversation ended (resolved, hand-off, drop-off) and any follow-up the \
            assistant promised.

            Reply in the same language the visitor used. Keep it under ~200 words and skip \
            sections that have no signal instead of writing filler.""";

    private static final String CACHE_KEY_PREFIX = "chat-session-summary:";

    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatMemoryService chatMemoryService;
    private final TurLlmSummaryService llmSummaryService;
    private final TurChatSlotAuditService slotAuditService;

    public TurChatSessionAPI(TurChatFlowEngineService chatFlowEngineService,
            TurChatMemoryService chatMemoryService,
            TurLlmSummaryService llmSummaryService,
            TurChatSlotAuditService slotAuditService) {
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatMemoryService = chatMemoryService;
        this.llmSummaryService = llmSummaryService;
        this.slotAuditService = slotAuditService;
    }

    @Operation(summary = "List slots captured during a chat session, merged into a "
            + "single flat map at the JSON root. Slots are conversation-scoped — the "
            + "AI agent treats them as globally addressable by name across flows, so "
            + "values are aggregated from every active flow state and every historical "
            + "submission with the most recent write winning.")
    @GetMapping("/{conversationId}/slots")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatSessionSlotsDto sessionSlots(@PathVariable String conversationId) {
        return chatFlowEngineService.listSlotsForConversation(conversationId);
    }

    @Operation(summary = "List chat memory messages persisted for a session, "
            + "newest-trimmed-first. The backing store is selected by "
            + "turing.logging.engine (mongodb | redis | none); when disabled "
            + "the response carries an empty messages list and enabled=false. "
            + "Pass summary=true to also generate an AI summary of the transcript "
            + "into the 'memory' attribute (cached via TurLlmCacheService).")
    @GetMapping("/{conversationId}/messages")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatSessionMessagesDto sessionMessages(@PathVariable String conversationId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean summary,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        TurChatSessionMessagesDto base = chatMemoryService.listMessages(conversationId, limit);
        if (!summary || base.messages().isEmpty()) {
            return base;
        }
        TurLlmSummaryService.SummaryResult memory = llmSummaryService.generate(
                CACHE_KEY_PREFIX + conversationId,
                renderTranscript(base.messages()),
                MEMORY_SUMMARY_INSTRUCTIONS,
                regenerate);
        return new TurChatSessionMessagesDto(
                base.conversationId(),
                base.engine(),
                base.enabled(),
                base.messages(),
                memory);
    }

    @Operation(summary = "T60 — slot write audit trail. Returns every slot mutation "
            + "captured for this conversation across the four production write paths "
            + "(NODE, TOOL, ENDPOINT, EXTRACT), oldest first. The SlotInspector renders "
            + "this as a timeline with source badges + (old → new) deltas for LGPD/"
            + "GDPR auditing and flow debugging.")
    @GetMapping("/{conversationId}/slot-audit")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatSlotAuditDto slotAudit(@PathVariable String conversationId) {
        java.util.List<TurChatSlotAuditEntry> rows = slotAuditService.list(conversationId);
        java.util.List<TurChatSlotAuditDto.Entry> entries = rows.stream()
                .map(r -> new TurChatSlotAuditDto.Entry(
                        r.getId(), r.getSlotName(), r.getOldValue(), r.getNewValue(),
                        r.getSource() == null ? null : r.getSource().name(),
                        r.getOriginDetail(), r.getTs()))
                .toList();
        return new TurChatSlotAuditDto(conversationId, entries);
    }

    private static String renderTranscript(java.util.List<TurChatSessionMessageDto> messages) {
        StringBuilder sb = new StringBuilder(messages.size() * 120);
        for (TurChatSessionMessageDto m : messages) {
            String role = m.role() == null ? "unknown" : m.role();
            String content = m.content() == null ? "" : m.content();
            sb.append(role).append(": ").append(content).append('\n');
        }
        return sb.toString();
    }
}
