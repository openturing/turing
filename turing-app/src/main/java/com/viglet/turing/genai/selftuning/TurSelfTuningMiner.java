/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.selftuning;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.answer.TurAnswerSignalEvent;
import com.viglet.turing.persistence.repository.answer.TurAnswerSignalRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatanalytics.TurChatSessionFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * T447 / §XXIII.6 — mines recent failed conversations for an agent into a compact
 * "failure summary" the drafter feeds to the LLM. Reads the chat-analytics store
 * (Mongo/Redis); the NoOp store yields nothing, so this degrades to "no failures"
 * fail-open. A failure = an ABANDONED/ERROR/HANDOFF outcome, NEGATIVE/FRUSTRATED
 * sentiment, or a turn with tool errors.
 *
 * <p>T845 / §LXII.14 — the failure picture also folds in answer-level
 * relevance-miss signals (a facet refined <em>after</em> reading the answer, a
 * thumbs-down) recorded by the answer surface and keyed by the same agent, so the
 * drafter learns from answer feedback, not just chat sessions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSelfTuningMiner {

    private static final int WINDOW_DAYS = 7;
    private static final int MAX_SESSIONS = 200;
    private static final int MAX_SUMMARY_LINES = 20;
    private static final int MAX_ANSWER_SIGNAL_LINES = 10;

    private final TurChatAnalyticsStore analyticsStore;
    private final TurAnswerSignalRepository answerSignalRepository;

    public TurSelfTuningMiner(TurChatAnalyticsStore analyticsStore,
            TurAnswerSignalRepository answerSignalRepository) {
        this.analyticsStore = analyticsStore;
        this.answerSignalRepository = answerSignalRepository;
    }

    /** A mined failure picture: how many failing sessions and a short digest. */
    public record FailureSummary(int count, String digest) {
        public boolean isEmpty() {
            return count == 0;
        }
    }

    public FailureSummary mine(String agentId) {
        List<Map<String, Object>> failing = findFailingSessions(agentId);
        StringBuilder digest = new StringBuilder();
        int shown = 0;
        for (Map<String, Object> s : failing) {
            if (shown++ < MAX_SUMMARY_LINES) {
                digest.append("- outcome=").append(str(s.get("outcome")))
                        .append(", sentiment=").append(str(s.get("sentiment")))
                        .append(", toolErrors=").append(num(s.get("totalToolErrors")))
                        .append(", turns=").append(num(s.get("totalTurns")))
                        .append("\n");
            }
        }
        // T845 — fold in answer-level relevance-miss signals for the same agent.
        List<TurAnswerSignalEvent> answerMisses = findAnswerMisses(agentId);
        int answerShown = 0;
        for (TurAnswerSignalEvent m : answerMisses) {
            if (answerShown++ < MAX_ANSWER_SIGNAL_LINES) {
                digest.append("- answerSignal=").append(m.getSignal())
                        .append(", query=").append(str(m.getQuery()))
                        .append("\n");
            }
        }
        return new FailureSummary(failing.size() + answerMisses.size(), digest.toString().trim());
    }

    /**
     * The agent's recent answer-level relevance-miss signals (facet-refined-after-answer,
     * thumbs-down) within the same window. Fail-open to an empty list so a repository
     * error never breaks a tuning cycle. Ordered newest-first by the repository.
     *
     * @since 2026.3.4
     */
    public List<TurAnswerSignalEvent> findAnswerMisses(String agentId) {
        Instant from = Instant.now().minus(Duration.ofDays(WINDOW_DAYS));
        try {
            return answerSignalRepository
                    .findByAgentIdAndRelevanceMissTrueAndCreatedAtAfterOrderByCreatedAtDesc(agentId, from);
        } catch (RuntimeException e) {
            log.warn("[SelfTuning] answer-signal mining failed for agent={}: {} — ignoring",
                    agentId, e.getMessage());
            return List.of();
        }
    }

    /**
     * The recent failing sessions for an agent (same 7-day window / 200-session
     * cap / failure definition {@link #mine} uses), as engine-agnostic session
     * maps. Fail-open to an empty list, so the NoOp store — or any store error —
     * yields "no failures". Exposed so T596's dataset importer can turn each
     * failing conversation into an eval-dataset row without re-implementing the
     * failure heuristic.
     *
     * @since 2026.3.4
     */
    public List<Map<String, Object>> findFailingSessions(String agentId) {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(WINDOW_DAYS));
        List<Map<String, Object>> sessions;
        try {
            sessions = analyticsStore.findRecentSessions(from, now,
                    new TurChatSessionFilter(agentId, null, null, null, null, null), MAX_SESSIONS);
        } catch (RuntimeException e) {
            log.warn("[SelfTuning] mining failed for agent={}: {} — treating as no failures",
                    agentId, e.getMessage());
            return List.of();
        }
        return sessions.stream().filter(TurSelfTuningMiner::isFailure).toList();
    }

    private static boolean isFailure(Map<String, Object> s) {
        String outcome = str(s.get("outcome"));
        String sentiment = str(s.get("sentiment"));
        return "ABANDONED".equals(outcome) || "ERROR".equals(outcome) || "HANDOFF".equals(outcome)
                || "NEGATIVE".equals(sentiment) || "FRUSTRATED".equals(sentiment)
                || num(s.get("totalToolErrors")) > 0;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
