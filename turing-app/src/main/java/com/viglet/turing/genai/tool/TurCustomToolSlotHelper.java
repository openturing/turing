/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.service.chatslots.TurChatSlotAuditService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

import lombok.extern.slf4j.Slf4j;

/**
 * Conversation-scoped helper exposed to Custom Tool Groovy scripts as the
 * binding variable {@code slots}. Lets a tool read and (more importantly)
 * <em>write</em> chat-flow variables on the current conversation — the same
 * variables the React portal reads via {@code useTuringSlot}/{@code useTuringSlots}.
 *
 * <p>This is what flips a Custom Tool from "returns text the LLM repeats"
 * to "writes the page state for downstream React components to render."
 * Scripts can call {@code slots.set("programas_match", json)} so a card
 * component picks up the value in its next polling tick — without the LLM
 * having to repeat the structured data in its reply.
 *
 * <p>Each helper instance is bound to a single {@code conversationId}, set
 * at construction time. {@link TurCustomToolCallbackService} pulls the id
 * out of the Spring AI {@code ToolContext} (populated by
 * {@code TurAgentChatExecutor}) and constructs a fresh helper per call.
 *
 * <p>Behavior when {@code conversationId} is {@code null} (rare — happens
 * only when the tool is invoked outside an active chat session, e.g. by a
 * standalone test): every operation is a no-op and returns the empty map.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
public class TurCustomToolSlotHelper {

    private final String conversationId;
    private final TurChatFlowStateRepository stateRepository;
    private final TurChatSlotEventBus slotEventBus;
    private final TurChatSlotAuditService slotAuditService;
    private final String toolName;

    public TurCustomToolSlotHelper(String conversationId,
            TurChatFlowStateRepository stateRepository,
            TurChatSlotEventBus slotEventBus) {
        this(conversationId, stateRepository, slotEventBus, null, null);
    }

    /**
     * Audit-aware constructor. The {@code slotAuditService} + {@code toolName}
     * pair lets the helper record a T60 audit row tagged with the originating
     * Custom Tool so the SlotInspector timeline shows "tool: my-script" on
     * each write. {@code TurCustomToolCallbackService.executeGroovy} threads
     * the active tool name in.
     *
     * @since 2026.3.1
     */
    public TurCustomToolSlotHelper(String conversationId,
            TurChatFlowStateRepository stateRepository,
            TurChatSlotEventBus slotEventBus,
            TurChatSlotAuditService slotAuditService,
            String toolName) {
        this.conversationId = conversationId;
        this.stateRepository = stateRepository;
        this.slotEventBus = slotEventBus;
        this.slotAuditService = slotAuditService;
        this.toolName = toolName;
    }

    /**
     * Sets {@code name = value} on every chat-flow state attached to the
     * current conversation (typically one state, but multi-flow conversations
     * may have several). Persists each touched state so the next polling tick
     * of a slot consumer (React portal, another tool) reads the fresh value.
     *
     * <p>A {@code null} or blank name is a no-op (defensive: a buggy script
     * passing {@code args.foo} where {@code foo} isn't supplied won't corrupt
     * the slot map). A {@code null} value is coerced to the empty string so
     * the variable still ends up keyed but cleared — same convention the
     * {@code slot} chat-flow node uses for cleared values.
     */
    public void set(String name, String value) {
        if (conversationId == null || name == null || name.isBlank()) {
            log.warn("[slots.set] NO-OP — conversationId={} name={} (Custom Tool invoked outside an "
                    + "active chat session, or missing 'turing.conversationId' in ToolContext)",
                    conversationId, name);
            return;
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            log.warn("[slots.set] NO-OP — no chat-flow states found for conversationId={} "
                    + "(slot '{}' write skipped)", conversationId, name);
            return;
        }
        String coerced = value == null ? "" : value;
        int valueLen = coerced.length();
        // Snapshot the pre-write merged value so the T60 audit row carries
        // the accurate "before" — null when the slot was previously unset.
        String previousValue = null;
        for (TurChatFlowState state : states) {
            String v = ChatFlowOps.readVariables(state).get(name);
            if (v != null) {
                previousValue = v;
            }
        }
        Map<String, String> mergedAfterWrite = new LinkedHashMap<>();
        for (TurChatFlowState state : states) {
            Map<String, String> vars = new LinkedHashMap<>(ChatFlowOps.readVariables(state));
            vars.put(name, coerced);
            ChatFlowOps.writeVariables(state, vars);
            stateRepository.save(state);
            mergedAfterWrite.putAll(vars);
            log.info("[slots.set] conv={} stateId={} slot='{}' wrote {} chars",
                    conversationId, state.getId(), name, valueLen);
        }
        // SSE notification — same merged-map contract as the writeSlot
        // endpoint, so subscribers don't have to special-case the source.
        slotEventBus.publish(conversationId, mergedAfterWrite);
        if (slotAuditService != null) {
            slotAuditService.record(conversationId, name, previousValue, coerced,
                    TurChatSlotAuditSource.TOOL,
                    toolName != null ? "tool=" + toolName : null);
        }
    }

    /**
     * Reads a slot value, merging across every state attached to the
     * current conversation (last writer wins when multiple states have it).
     * Returns {@code null} when the slot is unset.
     */
    public String get(String name) {
        if (conversationId == null || name == null) {
            return null;
        }
        return all().get(name);
    }

    /**
     * Returns the full slot map for the current conversation, merged across
     * every state. Empty map when the conversation has no states yet. The
     * returned map is a freshly-allocated copy — safe to mutate locally
     * without affecting the persisted state.
     */
    public Map<String, String> all() {
        if (conversationId == null) {
            return Collections.emptyMap();
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        for (TurChatFlowState state : states) {
            merged.putAll(ChatFlowOps.readVariables(state));
        }
        return merged;
    }

    /** The conversation this helper writes to. May be {@code null} (no-op mode). */
    public String getConversationId() {
        return conversationId;
    }
}
