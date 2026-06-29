/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * T445 / §XXIII.4 — the ambient / proactive copilot's detection logic.
 *
 * <p>The host writes ambient interaction signals as ordinary slots named
 * {@code signal.<kind>} whose value is a running interaction count (e.g.
 * {@code signal.return_policy = "5"} after the user opened five return-policy
 * pages). The {@code /chat/proactive/stream} SSE endpoint subscribes to the
 * conversation's slot bus (T63) and runs each post-write slot map through
 * {@link #evaluate} — when a signal's count crosses the agent's threshold it
 * returns ONE offer, then suppresses repeats for that signal kind and rate-limits
 * the conversation to one offer per throttle window.
 *
 * <p>The detection is a pure function over an injected clock + a per-subscription
 * {@link State}, so it is fully unit-testable without the reactive/SSE plumbing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurProactiveCopilotService {

    /** Slot-name prefix that marks an ambient interaction signal. */
    public static final String SIGNAL_PREFIX = "signal.";
    /** Fallback threshold when an agent has a non-positive configured value. */
    public static final int DEFAULT_THRESHOLD = 3;
    /** At most one proactive offer per conversation per this window. */
    public static final long DEFAULT_THROTTLE_MILLIS = 30_000L;

    /** Per-subscription state: last-offer time + the signal kinds already offered. */
    public static final class State {
        private long lastOfferMillis = Long.MIN_VALUE;
        private final Set<String> offered = new HashSet<>();
    }

    public State newState() {
        return new State();
    }

    /**
     * Evaluate a post-write slot map for a proactive offer.
     *
     * @return an offer when a fresh signal kind crosses {@code threshold} and the
     *         conversation is outside its throttle window; otherwise empty.
     */
    public Optional<TurProactiveSuggestionDto> evaluate(State state, String conversationId,
            Map<String, String> slots, int threshold, long throttleMillis, long nowMillis) {
        if (state == null || slots == null || slots.isEmpty()) {
            return Optional.empty();
        }
        int effThreshold = threshold > 0 ? threshold : DEFAULT_THRESHOLD;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            String kind = freshEligibleKind(entry, effThreshold, state);
            if (kind == null) {
                continue;
            }
            // Eligible signal found. Honour the per-conversation throttle: if an
            // offer was already made AND we're still inside the window, suppress now
            // WITHOUT marking it offered so it can fire after the window elapses.
            // (The Long.MIN_VALUE sentinel is guarded explicitly to avoid overflow
            // on the first-ever offer.)
            boolean withinThrottle = state.lastOfferMillis != Long.MIN_VALUE
                    && nowMillis - state.lastOfferMillis < throttleMillis;
            if (withinThrottle) {
                return Optional.empty();
            }
            state.offered.add(kind);
            state.lastOfferMillis = nowMillis;
            return Optional.of(buildSuggestion(conversationId, kind, parseCount(entry.getValue())));
        }
        return Optional.empty();
    }

    /**
     * The signal kind for a slot entry that is a fresh ({@code signal.<kind>} not
     * yet offered) signal whose count has reached {@code threshold}, or {@code null}.
     */
    private static String freshEligibleKind(Map.Entry<String, String> entry, int threshold,
            State state) {
        String name = entry.getKey();
        if (name == null || !name.startsWith(SIGNAL_PREFIX)) {
            return null;
        }
        String kind = name.substring(SIGNAL_PREFIX.length()).trim();
        if (kind.isEmpty() || state.offered.contains(kind) || parseCount(entry.getValue()) < threshold) {
            return null;
        }
        return kind;
    }

    private static int parseCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static TurProactiveSuggestionDto buildSuggestion(String conversationId, String kind,
            int count) {
        String label = kind.replace('_', ' ').replace('.', ' ').trim();
        String message = "I noticed you've engaged with several \"%s\" items — want help with that?"
                .formatted(label);
        String suggestedPrompt = "Help me with " + label;
        return new TurProactiveSuggestionDto(conversationId, kind, count, message, suggestedPrompt);
    }
}
