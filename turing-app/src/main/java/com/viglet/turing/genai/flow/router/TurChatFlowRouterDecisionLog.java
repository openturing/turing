/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.router;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * T89 / §VII.10.f — sink for chat-flow router decisions.
 *
 * <p>Two outputs from a single {@link #record(TurChatFlowRouterDecision)}
 * call:
 *
 * <ol>
 *   <li><b>Structured log line</b> — one {@code [FlowRouter] decision …}
 *       line per router call, with the candidate flows + scores + winner +
 *       method as parseable {@code key=value} fields. This is the durable
 *       record (it survives in the log aggregator long after the in-memory
 *       ring evicts it).</li>
 *   <li><b>Bounded in-memory ring</b> — the most recent decisions, kept both
 *       globally and per-conversation, so an operator can pull "the router
 *       decisions for conversation X" through the analytics REST surface
 *       while triaging a live complaint, without grepping logs.</li>
 * </ol>
 *
 * <p>The service is wired into {@code TurChatFlowEngineService} through a
 * Spring-managed singleton accessed via {@link #getInstance()} (the same
 * pattern as {@code TurPiiSlotService}): the engine carries 11 constructor
 * collaborators already and recording a decision is a best-effort
 * cross-cutting concern that must never break a chat turn, so the engine
 * calls the null-safe static {@link #recordSafely(TurChatFlowRouterDecision)}
 * rather than taking a hard dependency. Pure unit tests that don't bootstrap
 * Spring see {@code getInstance() == null} and recording degrades to a no-op;
 * the buffer/log behaviour itself is testable by instantiating the service
 * directly.
 *
 * <p>The ring is per-process and ephemeral — clustered deploys keep one ring
 * per node, and a restart clears it. That is acceptable: the structured log
 * is the durable artifact; the ring is a convenience for live inspection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatFlowRouterDecisionLog {

    /** Max distinct conversations retained in the per-conversation ring. */
    static final int MAX_CONVERSATIONS = 2000;

    /** Max decisions retained per conversation. */
    static final int MAX_PER_CONVERSATION = 50;

    /** Max decisions retained in the global recency ring. */
    static final int MAX_GLOBAL = 2000;

    private static volatile TurChatFlowRouterDecisionLog instance;

    /**
     * Access-order LRU of conversationId → recent decisions. Bounded by
     * {@link #MAX_CONVERSATIONS} so a long-running process with millions of
     * conversations doesn't leak; each bucket is bounded by
     * {@link #MAX_PER_CONVERSATION}.
     */
    private final Map<String, Deque<TurChatFlowRouterDecision>> byConversation =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(256, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, Deque<TurChatFlowRouterDecision>> eldest) {
                            return size() > MAX_CONVERSATIONS;
                        }
                    });

    private final Deque<TurChatFlowRouterDecision> global = new ArrayDeque<>();

    @PostConstruct
    void register() {
        instance = this;
    }

    @PreDestroy
    void unregister() {
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Spring-managed singleton accessor. May return {@code null} in test
     * environments that don't bootstrap the full context — callers must
     * null-check (or use {@link #recordSafely(TurChatFlowRouterDecision)}).
     */
    public static TurChatFlowRouterDecisionLog getInstance() {
        return instance;
    }

    /**
     * Null-safe, never-throwing convenience for hot-path callers (the flow
     * engine). Records the decision through the Spring singleton when one is
     * registered; a no-op otherwise. Swallows any failure so the analytics
     * layer can never break a chat turn.
     */
    public static void recordSafely(TurChatFlowRouterDecision decision) {
        TurChatFlowRouterDecisionLog log = instance;
        if (log == null || decision == null) {
            return;
        }
        try {
            log.record(decision);
        } catch (RuntimeException e) {
            TurChatFlowRouterDecisionLog.log.debug(
                    "[FlowRouter] decision record failed: {}", e.getMessage());
        }
    }

    /**
     * Emits the structured log line and retains the decision in both rings.
     */
    public void record(TurChatFlowRouterDecision decision) {
        if (decision == null) {
            return;
        }
        log.info("[FlowRouter] decision {}", format(decision));
        if (decision.conversationId() != null && !decision.conversationId().isBlank()) {
            synchronized (byConversation) {
                Deque<TurChatFlowRouterDecision> bucket = byConversation
                        .computeIfAbsent(decision.conversationId(), k -> new ArrayDeque<>());
                bucket.addLast(decision);
                while (bucket.size() > MAX_PER_CONVERSATION) {
                    bucket.removeFirst();
                }
            }
        }
        synchronized (global) {
            global.addLast(decision);
            while (global.size() > MAX_GLOBAL) {
                global.removeFirst();
            }
        }
    }

    /**
     * Most recent decisions for one conversation, newest first, capped at
     * {@code limit}. Empty when the conversation has no retained decisions
     * (never routed, or evicted from the ring).
     */
    public List<TurChatFlowRouterDecision> recentForConversation(String conversationId, int limit) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        synchronized (byConversation) {
            Deque<TurChatFlowRouterDecision> bucket = byConversation.get(conversationId);
            return newestFirst(bucket, limit);
        }
    }

    /** Most recent decisions across all conversations, newest first. */
    public List<TurChatFlowRouterDecision> recent(int limit) {
        synchronized (global) {
            return newestFirst(global, limit);
        }
    }

    private static List<TurChatFlowRouterDecision> newestFirst(
            Deque<TurChatFlowRouterDecision> source, int limit) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int cap = limit <= 0 ? source.size() : Math.min(limit, source.size());
        List<TurChatFlowRouterDecision> out = new ArrayList<>(cap);
        var it = source.descendingIterator();
        while (it.hasNext() && out.size() < cap) {
            out.add(it.next());
        }
        return out;
    }

    /**
     * Renders a decision as a single parseable {@code key=value} line. The
     * candidate list is rendered as {@code name:score} pairs with the winner
     * marked, so a log grep answers "B2C won at 4.2 over B2B at 1.1" without
     * a JSON parse.
     */
    static String format(TurChatFlowRouterDecision d) {
        StringJoiner candidates = new StringJoiner(", ", "[", "]");
        for (TurChatFlowRouterDecision.Candidate c : d.candidates()) {
            StringBuilder cell = new StringBuilder();
            if (c.winner()) {
                cell.append('*');
            }
            cell.append(c.flowName() == null ? c.flowId() : c.flowName());
            cell.append(':').append(c.score() == null ? "-" : fmt(c.score()));
            candidates.add(cell.toString());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("agent=").append(d.agentId())
                .append(" conversation=").append(d.conversationId())
                .append(" method=").append(d.method())
                .append(" winner=").append(d.winnerFlowId() == null ? "none" : d.winnerFlowId());
        if (d.winnerFlowName() != null) {
            sb.append('(').append(d.winnerFlowName()).append(')');
        }
        sb.append(" candidates=").append(candidates);
        if (d.procedural()) {
            sb.append(" best=").append(fmt(d.bestScore()))
                    .append(" second=").append(fmt(d.secondScore()))
                    .append(" ratio=").append(fmt(d.dominanceRatio()));
        }
        if (d.detail() != null && !d.detail().isBlank()) {
            sb.append(" detail=").append(d.detail());
        }
        sb.append(" msg=\"").append(d.userMessage() == null ? "" : d.userMessage()).append('"');
        return sb.toString();
    }

    private static String fmt(Float value) {
        if (value == null) {
            return "-";
        }
        if (value.isInfinite()) {
            return "inf";
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
