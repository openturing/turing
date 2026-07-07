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
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSelfTuningMiner {

    private static final int WINDOW_DAYS = 7;
    private static final int MAX_SESSIONS = 200;
    private static final int MAX_SUMMARY_LINES = 20;

    private final TurChatAnalyticsStore analyticsStore;

    public TurSelfTuningMiner(TurChatAnalyticsStore analyticsStore) {
        this.analyticsStore = analyticsStore;
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
        return new FailureSummary(failing.size(), digest.toString().trim());
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
