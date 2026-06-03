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

import java.util.List;

/**
 * T89 / §VII.10.f — a structured, immutable record of one chat-flow router
 * decision: the candidate flows that competed, the score each earned in the
 * deterministic keyword pass, the winner, and the mechanism that picked it.
 *
 * <p>Captures the answer to "<i>why did B2B lose to B2C on this message?</i>"
 * — the question an operator asks after a customer complains the conversation
 * went down the wrong track. {@link TurChatFlowRouterDecisionLog} emits one
 * structured log line per decision and retains a bounded ring for live
 * inspection through the analytics REST surface.
 *
 * <p>Scores are only populated for the {@link TurChatFlowRouterMethod#PROCEDURAL}
 * keyword pass — the LLM router does not expose per-candidate scores, so for
 * an {@code LLM}/{@code LLM_CACHE} decision the {@link Candidate#score()} is
 * the keyword score from the (undecided) procedural pass when one ran, else
 * {@code null}. {@link #procedural()} stays {@code true} whenever the keyword
 * scorer produced any score, even when it was too ambiguous to commit and the
 * decision fell through to the LLM — so a complaint about an LLM pick can
 * still show how close the keyword race was.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowRouterDecision(
        long epochMillis,
        String agentId,
        String conversationId,
        String userMessage,
        TurChatFlowRouterMethod method,
        String winnerFlowId,
        String winnerFlowName,
        List<Candidate> candidates,
        Float bestScore,
        Float secondScore,
        Float dominanceRatio,
        boolean procedural,
        String detail) {

    /** Max user-message length retained on a decision (chars). */
    public static final int MAX_MESSAGE_LENGTH = 280;

    public TurChatFlowRouterDecision {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        userMessage = truncate(userMessage);
    }

    /**
     * One flow that competed in the routing pass.
     *
     * @param flowId   the candidate flow id
     * @param flowName the candidate flow display name (may be {@code null})
     * @param score    the keyword-pass score; {@code null} when the
     *                 procedural pass did not run or did not score this flow
     * @param winner   {@code true} for the flow the router ultimately chose
     */
    public record Candidate(String flowId, String flowName, Float score, boolean winner) {
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= MAX_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
