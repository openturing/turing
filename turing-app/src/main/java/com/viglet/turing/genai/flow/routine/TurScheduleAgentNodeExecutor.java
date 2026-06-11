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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;
import com.viglet.turing.sn.TurSNConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * T48 — runtime for the {@code scheduleAgent} chat-flow node. Fires the
 * configured {@link TurRoutine} onto the {@link TurSNConstants#ROUTINE_QUEUE}
 * for an async worker, parks the flow on this node with bookkeeping in the
 * conversation variables, and returns control to the chat turn immediately.
 *
 * <p><b>Lifecycle on a single {@code scheduleAgent} node</b>:
 * <ol>
 *   <li><b>First entry</b> — no pending marker on the node yet. The
 *       executor resolves the routine, interpolates {@code aiInstruction}
 *       as the JSON input template, enqueues a {@link TurScheduleAgentMessage}
 *       and writes {@code __scheduleAgent_pending_<nodeId>=routineId} +
 *       {@code __scheduleAgent_startedAt_<nodeId>=epochMillis} into the
 *       variables map. Returns {@link Outcome#WAITING_FIRED} so the engine
 *       stops walking the transparent chain and returns to the client.</li>
 *   <li><b>Re-entry while pending</b> — bookkeeping marker is set. If
 *       {@code outputVariable} is now non-blank (the routine wrote its
 *       slot via the bus → auto-resume nudged us back here),
 *       bookkeeping is cleared and {@link Outcome#COMPLETED} is returned
 *       so the engine advances. If the deadline elapsed,
 *       bookkeeping is cleared and {@link Outcome#TIMEOUT} is returned
 *       so the engine routes via the {@code timeout} sourceHandle (or
 *       first outgoing edge as fallback). Otherwise {@link Outcome#WAITING_POLL}
 *       again.</li>
 * </ol>
 *
 * <p>The executor never advances the cursor itself — that's the engine's
 * job, identical to {@code functionCall} contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurScheduleAgentNodeExecutor {

    private static final String PENDING_PREFIX = "__scheduleAgent_pending_";
    private static final String STARTED_AT_PREFIX = "__scheduleAgent_startedAt_";
    private static final int DEFAULT_TIMEOUT_FALLBACK_MS = 60_000;

    /** Outgoing edge sourceHandle used to wire a timeout branch. */
    public static final String TIMEOUT_HANDLE = "timeout";

    private final TurRoutineRepository routineRepository;
    private final JmsMessagingTemplate jmsMessagingTemplate;
    private final com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation;

    public TurScheduleAgentNodeExecutor(TurRoutineRepository routineRepository,
            JmsMessagingTemplate jmsMessagingTemplate,
            com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation) {
        this.routineRepository = routineRepository;
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.turJmsTenantPropagation = turJmsTenantPropagation;
    }

    /**
     * Runs (or polls) the {@code scheduleAgent} node referenced by
     * {@code node} against {@code state}. Mutates the in-memory variables
     * map; the caller persists state.
     */
    public Outcome execute(TurChatFlowState state, ChatFlowNode node) {
        if (state == null || node == null) {
            return Outcome.FAILED;
        }
        Map<String, String> variables = new LinkedHashMap<>(ChatFlowOps.readVariables(state));

        String nodeId = node.id();
        String pendingKey = PENDING_PREFIX + nodeId;
        String startedAtKey = STARTED_AT_PREFIX + nodeId;
        String outputSlot = trimOrNull(node.outputVariable());

        if (variables.containsKey(pendingKey)) {
            return poll(state, node, variables, pendingKey, startedAtKey, outputSlot);
        }
        return fire(state, node, variables, pendingKey, startedAtKey, outputSlot);
    }

    private Outcome fire(TurChatFlowState state,
            ChatFlowNode node,
            Map<String, String> variables,
            String pendingKey,
            String startedAtKey,
            String outputSlot) {
        String routineId = trimOrNull(node.routineId());
        if (routineId == null) {
            log.warn("[FlowOps/scheduleAgent] node '{}' has no routineId — skipping", node.id());
            return Outcome.FAILED;
        }
        Optional<TurRoutine> routineOpt = routineRepository.findById(routineId);
        if (routineOpt.isEmpty()) {
            log.warn("[FlowOps/scheduleAgent] routine '{}' not found for node '{}' — skipping",
                    routineId, node.id());
            return Outcome.FAILED;
        }
        TurRoutine routine = routineOpt.get();
        if (!routine.isEnabled()) {
            log.warn("[FlowOps/scheduleAgent] routine '{}' is disabled — skipping node '{}'",
                    routineId, node.id());
            return Outcome.FAILED;
        }

        String inputJson = (node.aiInstruction() == null || node.aiInstruction().isBlank())
                ? "{}"
                : ChatFlowOps.interpolateVariables(node.aiInstruction(), variables);

        TurScheduleAgentMessage message = new TurScheduleAgentMessage(
                routineId,
                state.getConversationId(),
                node.id(),
                outputSlot,
                inputJson);

        try {
            jmsMessagingTemplate.convertAndSend(TurSNConstants.ROUTINE_QUEUE, message,
                    turJmsTenantPropagation.headers());
        } catch (RuntimeException e) {
            log.warn("[FlowOps/scheduleAgent] failed to enqueue routine '{}' for node '{}': {}",
                    routineId, node.id(), e.getMessage(), e);
            return Outcome.FAILED;
        }

        variables.put(pendingKey, routineId);
        variables.put(startedAtKey, Long.toString(Instant.now().toEpochMilli()));
        ChatFlowOps.writeVariables(state, variables);
        log.info("[FlowOps/scheduleAgent] node '{}' fired routine '{}' on conv '{}' (slot '{}')",
                node.id(), routineId, state.getConversationId(), outputSlot);
        return Outcome.WAITING_FIRED;
    }

    private Outcome poll(TurChatFlowState state,
            ChatFlowNode node,
            Map<String, String> variables,
            String pendingKey,
            String startedAtKey,
            String outputSlot) {
        if (outputSlot != null) {
            String current = variables.get(outputSlot);
            if (current != null && !current.isBlank()) {
                variables.remove(pendingKey);
                variables.remove(startedAtKey);
                ChatFlowOps.writeVariables(state, variables);
                log.info("[FlowOps/scheduleAgent] node '{}' routine completed — slot '{}' filled on conv '{}'",
                        node.id(), outputSlot, state.getConversationId());
                return Outcome.COMPLETED;
            }
        }
        long startedAtMs = parseLong(variables.get(startedAtKey));
        long deadlineMs = resolveTimeoutMs(node);
        if (startedAtMs > 0 && Instant.now().toEpochMilli() - startedAtMs >= deadlineMs) {
            variables.remove(pendingKey);
            variables.remove(startedAtKey);
            ChatFlowOps.writeVariables(state, variables);
            log.warn("[FlowOps/scheduleAgent] node '{}' timed out after {}ms on conv '{}'",
                    node.id(), deadlineMs, state.getConversationId());
            return Outcome.TIMEOUT;
        }
        return Outcome.WAITING_POLL;
    }

    private long resolveTimeoutMs(ChatFlowNode node) {
        Integer override = node.routineTimeoutMs();
        if (override != null && override > 0) {
            return override;
        }
        return routineRepository.findById(trimOrNull(node.routineId()))
                .map(TurRoutine::getDefaultTimeoutMs)
                .filter(v -> v > 0)
                .map(Integer::longValue)
                .orElse((long) DEFAULT_TIMEOUT_FALLBACK_MS);
    }

    /**
     * Advances {@code state} along the {@code timeout}-handled outgoing
     * edge of {@code current}; falls back to the first outgoing edge when
     * no edge is explicitly wired to the timeout branch. Mirrors the
     * lenient contract of {@link ChatFlowOps#advanceToFirstEdge}.
     */
    public static String advanceOnTimeout(TurChatFlowState state,
            ChatFlowGraph graph,
            ChatFlowNode current) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(current.id());
        Optional<ChatFlowEdge> timeoutEdge = outgoing.stream()
                .filter(e -> TIMEOUT_HANDLE.equals(e.sourceHandle()))
                .findFirst();
        ChatFlowEdge picked = timeoutEdge.orElseGet(() -> outgoing.isEmpty() ? null : outgoing.get(0));
        if (picked == null) {
            log.info("[FlowOps/scheduleAgent] node '{}' has no outgoing edges on timeout — staying",
                    current.id());
            return state.getCurrentNodeId();
        }
        state.setCurrentNodeId(picked.target());
        return picked.target();
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Result of one call to {@link #execute(TurChatFlowState, ChatFlowNode)}. */
    public enum Outcome {
        /**
         * Routine just enqueued — markers were freshly written by this call.
         * Caller stops walking AND publishes the slot map so the chat UI
         * picks up the pending indicator. Only emitted on the very first
         * entry to a {@code scheduleAgent} node within a flow run.
         */
        WAITING_FIRED,
        /**
         * Routine still in flight, markers already set on a previous turn.
         * Caller stops walking but must NOT republish — that would feed
         * the auto-resume listener and create a publish→walk→publish loop.
         */
        WAITING_POLL,
        /** Output slot now populated — caller advances to first edge. */
        COMPLETED,
        /** Deadline expired — caller routes via {@link #advanceOnTimeout}. */
        TIMEOUT,
        /** Resolution or enqueue failure — caller advances and logs. */
        FAILED
    }
}
