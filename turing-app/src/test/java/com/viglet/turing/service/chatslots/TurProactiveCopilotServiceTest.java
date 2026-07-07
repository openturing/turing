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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TurProactiveCopilotServiceTest {

    private final TurProactiveCopilotService service = new TurProactiveCopilotService();

    private static Map<String, String> slots(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void offersWhenSignalCrossesThreshold() {
        var state = service.newState();
        Optional<TurProactiveSuggestionDto> out = service.evaluate(
                state, "c1", slots("signal.return_policy", "3"), 3, 30_000L, 1_000L);
        assertThat(out).isPresent();
        assertThat(out.get().signal()).isEqualTo("return_policy");
        assertThat(out.get().count()).isEqualTo(3);
        assertThat(out.get().message()).contains("return policy");
        assertThat(out.get().suggestedPrompt()).isEqualTo("Help me with return policy");
        assertThat(out.get().conversationId()).isEqualTo("c1");
    }

    @Test
    void doesNotOfferBelowThreshold() {
        var state = service.newState();
        assertThat(service.evaluate(state, "c1", slots("signal.x", "2"), 3, 30_000L, 1_000L))
                .isEmpty();
    }

    @Test
    void ignoresNonSignalSlots() {
        var state = service.newState();
        assertThat(service.evaluate(state, "c1", slots("color", "red", "qty", "9"), 1, 30_000L, 1L))
                .isEmpty();
    }

    @Test
    void offersEachSignalKindOnlyOnce() {
        var state = service.newState();
        // First crossing fires.
        assertThat(service.evaluate(state, "c1", slots("signal.x", "3"), 3, 0L, 1L)).isPresent();
        // Same kind again (even higher count) does not re-fire (throttle=0 isolates this).
        assertThat(service.evaluate(state, "c1", slots("signal.x", "9"), 3, 0L, 2L)).isEmpty();
    }

    @Test
    void throttlesAcrossKindsWithinWindowThenAllowsAfter() {
        var state = service.newState();
        assertThat(service.evaluate(state, "c1", slots("signal.a", "3"), 3, 30_000L, 1_000L))
                .isPresent();
        // A different eligible signal within the throttle window is suppressed...
        assertThat(service.evaluate(state, "c1", slots("signal.b", "5"), 3, 30_000L, 5_000L))
                .isEmpty();
        // ...and offered once the window elapses.
        assertThat(service.evaluate(state, "c1", slots("signal.b", "5"), 3, 30_000L, 40_000L))
                .isPresent();
    }

    @Test
    void nonNumericSignalValueNeverTriggers() {
        var state = service.newState();
        assertThat(service.evaluate(state, "c1", slots("signal.x", "yes"), 1, 30_000L, 1L))
                .isEmpty();
    }

    @Test
    void emptyOrNullSlotsAreSafe() {
        var state = service.newState();
        assertThat(service.evaluate(state, "c1", Map.of(), 3, 30_000L, 1L)).isEmpty();
        assertThat(service.evaluate(state, "c1", null, 3, 30_000L, 1L)).isEmpty();
    }

    @Test
    void nonPositiveThresholdFallsBackToDefault() {
        var state = service.newState();
        // threshold 0 → DEFAULT_THRESHOLD (3); count 2 stays below.
        assertThat(service.evaluate(state, "c1", slots("signal.x", "2"), 0, 30_000L, 1L)).isEmpty();
        assertThat(service.evaluate(state, "c1", slots("signal.x", "3"), 0, 30_000L, 1L)).isPresent();
    }
}
