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

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto;
import com.viglet.turing.genai.spectator.TurChatMessageEvent;
import com.viglet.turing.genai.spectator.TurChatMessageEventBus;
import com.viglet.turing.genai.citation.TurCitationDriftService;
import com.viglet.turing.genai.spectator.TurCopilotService;
import com.viglet.turing.genai.spectator.TurCopilotService.ManualTurnResult;
import com.viglet.turing.genai.workspace.TurWorkspaceEvent;
import com.viglet.turing.genai.workspace.TurWorkspaceEventBus;
import com.viglet.turing.persistence.dto.agent.TurChatCitationDriftDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionExportDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.dto.agent.TurChatSlotAuditDto;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.chatslots.TurChatSlotAuditService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;
import com.viglet.turing.service.chatslots.TurChatSlotSseRegistry;
import com.viglet.turing.service.chatslots.TurSubmissionRetentionService;
import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

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
    private final TurSubmissionRetentionService submissionRetentionService;
    private final TurChatMessageEventBus chatMessageEventBus;
    private final TurChatSlotEventBus slotEventBus;
    private final TurWorkspaceEventBus workspaceEventBus;
    private final TurChatSlotSseRegistry slotSseRegistry;
    private final TurCopilotService copilotService;
    private final TurCitationDriftService citationDriftService;

    public TurChatSessionAPI(TurChatFlowEngineService chatFlowEngineService,
            TurChatMemoryService chatMemoryService,
            TurLlmSummaryService llmSummaryService,
            TurChatSlotAuditService slotAuditService,
            TurSubmissionRetentionService submissionRetentionService,
            TurChatMessageEventBus chatMessageEventBus,
            TurChatSlotEventBus slotEventBus,
            TurWorkspaceEventBus workspaceEventBus,
            TurChatSlotSseRegistry slotSseRegistry,
            TurCopilotService copilotService,
            TurCitationDriftService citationDriftService) {
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatMemoryService = chatMemoryService;
        this.llmSummaryService = llmSummaryService;
        this.slotAuditService = slotAuditService;
        this.submissionRetentionService = submissionRetentionService;
        this.chatMessageEventBus = chatMessageEventBus;
        this.slotEventBus = slotEventBus;
        this.workspaceEventBus = workspaceEventBus;
        this.slotSseRegistry = slotSseRegistry;
        this.copilotService = copilotService;
        this.citationDriftService = citationDriftService;
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
        return new TurChatSlotAuditDto(conversationId, slotAuditEntries(conversationId));
    }

    @Operation(summary = "T155 — citation drift verdicts. Lists every Anthropic Citation "
            + "persisted for this conversation with its current drift status: whether the "
            + "cited source has been re-indexed since the answer or its cited passage is no "
            + "longer present (citationStale). Answers the compliance question 'what document "
            + "did this answer cite, and has it changed since?'. Pass recheck=true to "
            + "re-resolve the citations against the live index synchronously before returning "
            + "(on-demand 'prove it now'); requires turing.genai.citation-drift.enabled.")
    @GetMapping("/{conversationId}/citation-drift")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatCitationDriftDto citationDrift(@PathVariable String conversationId,
            @RequestParam(defaultValue = "false") boolean recheck) {
        var records = (recheck && citationDriftService.isEnabled())
                ? citationDriftService.recheckConversation(conversationId)
                : citationDriftService.findByConversation(conversationId);
        return TurChatCitationDriftDto.from(conversationId, records);
    }

    @Operation(summary = "T65 — full snapshot export of a conversation's runtime state "
            + "for debugging / regression. Bundles the raw chat_flow_state rows (cursor "
            + "node + parsed variable map per flow), finished submissions, the merged slot "
            + "map, the chat-memory transcript, and the T60 slot-write audit trail into one "
            + "JSON document. Served as an attachment so the admin 'Export conversation' "
            + "button downloads it directly. pii_* values stay encrypted/redacted exactly "
            + "as stored.")
    @GetMapping("/{conversationId}/export")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public ResponseEntity<TurChatSessionExportDto> exportConversation(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1000") int transcriptLimit) {
        TurChatSessionMessagesDto transcript = chatMemoryService.listMessages(conversationId, transcriptLimit);
        TurChatSessionExportDto export = new TurChatSessionExportDto(
                conversationId,
                java.time.LocalDateTime.now(ZoneId.systemDefault()),
                chatFlowEngineService.exportFlowStates(conversationId),
                chatFlowEngineService.listSubmissionsForConversation(conversationId),
                chatFlowEngineService.listSlotsForConversation(conversationId).slots(),
                transcript.engine(),
                transcript.enabled(),
                transcript.messages(),
                slotAuditEntries(conversationId));

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("conversation-" + sanitizeForFilename(conversationId) + ".json")
                .build();
        ResponseEntity<TurChatSessionExportDto> response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(export);
        // T66 / §VII.6.g — DELETE_AFTER_EXPORT agents treat this export as the
        // system-of-record archive; purge their copy of the conversation's
        // submissions now that the bundle is built. No-op for the other modes.
        submissionRetentionService.applyPostExportRetention(conversationId);
        return response;
    }

    // ---------------------------------------------------------------------
    // T120 — Spectator + Co-pilot mode
    //
    // A spectating operator opens /conversation/{id}?spectate=true in the admin
    // console and watches a conversation they did not initiate, live, over three
    // conversation-scoped SSE streams below (message / slots / workspace).
    // Toggling ?manual=true lets them take the wheel for a turn via the
    // manual-turn endpoint — the LLM is skipped and the operator's text becomes
    // the assistant message, written through the same flow-advance + telemetry
    // path so slots and analytics stay consistent.
    // ---------------------------------------------------------------------

    @Operation(summary = "T120 — current flow/cursor state for a conversation: the "
            + "active flow id/name, the current node, the guardrail method, A/B "
            + "experiment metadata, and a suspended reason when the cursor parks on a "
            + "suspend/humanApproval node. Powers the spectator header.")
    @GetMapping("/{conversationId}/state")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public ConversationStateDto conversationState(@PathVariable String conversationId) {
        return chatFlowEngineService.getConversationState(conversationId);
    }

    @Operation(summary = "T120 — live message stream for a spectated conversation. "
            + "Emits a transcript snapshot (one event per stored chat-memory message) "
            + "then one event per completed turn (visitor + assistant), whether the "
            + "assistant reply came from the LLM or an operator who took the wheel "
            + "(manual=true). 25s comment heartbeat; single-node bus, same caveat as "
            + "the slot/workspace streams.")
    @GetMapping(value = "/{conversationId}/spectate/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public Flux<ServerSentEvent<TurChatMessageEvent>> spectateMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "200") int snapshotLimit) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        Flux<TurChatMessageEvent> snapshot = Flux.fromIterable(
                chatMemoryService.listMessages(conversationId, snapshotLimit).messages())
                .map(m -> new TurChatMessageEvent(conversationId, m.role(), m.content(), false, 0L));
        Flux<ServerSentEvent<TurChatMessageEvent>> data =
                Flux.concat(snapshot, chatMessageEventBus.subscribe(conversationId))
                        .map(event -> ServerSentEvent.<TurChatMessageEvent>builder(event).build());
        return Flux.merge(data, TurChatSessionAPI.<TurChatMessageEvent>heartbeat())
                .doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
                        TurChatSlotSseRegistry.Mode.SPECTATE))
                .doFinally(sig -> slotSseRegistry.release(conversationId,
                        TurChatSlotSseRegistry.Mode.SPECTATE));
    }

    @Operation(summary = "T120 — conversation-scoped live slot stream for spectator "
            + "mode. Prepends the current merged slot snapshot, then relays every slot "
            + "write. Mirrors the public /api/sn/{site}/chat/slots/stream but keyed only "
            + "on the conversation, so a spectator needs no site/agent context.")
    @GetMapping(value = "/{conversationId}/spectate/slots/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public Flux<ServerSentEvent<TurChatSessionSlotsDto>> spectateSlots(
            @PathVariable String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        TurChatSessionSlotsDto initial = chatFlowEngineService.listSlotsForConversation(conversationId);
        Flux<ServerSentEvent<TurChatSessionSlotsDto>> data =
                Flux.concat(Flux.just(initial), slotEventBus.subscribe(conversationId))
                        .map(event -> ServerSentEvent.<TurChatSessionSlotsDto>builder(event).build());
        return Flux.merge(data, TurChatSessionAPI.<TurChatSessionSlotsDto>heartbeat())
                .doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
                        TurChatSlotSseRegistry.Mode.SNAPSHOT))
                .doFinally(sig -> slotSseRegistry.release(conversationId,
                        TurChatSlotSseRegistry.Mode.SNAPSHOT));
    }

    @Operation(summary = "T120 — conversation-scoped live workspace stream for "
            + "spectator mode. Relays workspace blob put/delete metadata as it happens. "
            + "Live-only (no initial snapshot — the artifact list is agent-scoped and "
            + "the spectator path is keyed on the conversation alone).")
    @GetMapping(value = "/{conversationId}/spectate/workspace/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public Flux<ServerSentEvent<TurWorkspaceEvent>> spectateWorkspace(
            @PathVariable String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        Flux<ServerSentEvent<TurWorkspaceEvent>> data = workspaceEventBus.subscribe(conversationId)
                .map(event -> ServerSentEvent.<TurWorkspaceEvent>builder(event).build());
        return Flux.merge(data, TurChatSessionAPI.<TurWorkspaceEvent>heartbeat())
                .doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
                        TurChatSlotSseRegistry.Mode.WORKSPACE))
                .doFinally(sig -> slotSseRegistry.release(conversationId,
                        TurChatSlotSseRegistry.Mode.WORKSPACE));
    }

    /**
     * T120 — co-pilot "take the wheel": inject an operator-authored assistant
     * turn. Skips the LLM; the operator's {@code assistantMessage} becomes the
     * reply and (when paired with a {@code userMessage}) the flow advances
     * through the same path a normal turn uses, so slots/analytics stay
     * consistent. The reply reaches the visitor and any spectator via the
     * message bus.
     */
    @Operation(summary = "T120 — co-pilot manual turn. The operator's text becomes the "
            + "assistant message (LLM skipped); the flow advances and telemetry records "
            + "exactly as a normal turn, keeping slots and analytics consistent.")
    @PostMapping("/{conversationId}/manual-turn")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public ManualTurnResult manualTurn(@PathVariable String conversationId,
            @RequestBody ManualTurnRequest request) {
        return copilotService.manualTurn(conversationId,
                request == null ? null : request.agentId(),
                request == null ? null : request.flowId(),
                request == null ? null : request.userMessage(),
                request == null ? null : request.assistantMessage());
    }

    /**
     * Body of {@code POST /{conversationId}/manual-turn}. {@code agentId} and
     * {@code assistantMessage} are required; {@code flowId} defaults to the
     * conversation's active flow and {@code userMessage} is the visitor message
     * being answered (omit for a pure operator interjection that does not
     * advance the flow).
     */
    public record ManualTurnRequest(String agentId, String flowId, String userMessage,
            String assistantMessage) {
    }

    /** 25s comment heartbeat shared by the three spectator SSE streams. */
    private static <T> Flux<ServerSentEvent<T>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(25))
                .map(tick -> ServerSentEvent.<T>builder().comment("heartbeat").build());
    }

    private java.util.List<TurChatSlotAuditDto.Entry> slotAuditEntries(String conversationId) {
        java.util.List<TurChatSlotAuditEntry> rows = slotAuditService.list(conversationId);
        return rows.stream()
                .map(r -> new TurChatSlotAuditDto.Entry(
                        r.getId(), r.getSlotName(), r.getOldValue(), r.getNewValue(),
                        r.getSource() == null ? null : r.getSource().name(),
                        r.getOriginDetail(), r.getTs()))
                .toList();
    }

    /**
     * Keep the downloaded filename safe — conversation ids are opaque client
     * strings (cookie values) and could in theory carry path / header-injection
     * characters. Replace anything outside {@code [A-Za-z0-9._-]} with '_'.
     */
    private static String sanitizeForFilename(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "session";
        }
        String cleaned = conversationId.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
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
