/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T849 / §LXIII.4 — the publish policy for arriving transcript text: often
 * enough to read, rarely enough not to flood the SSE bus.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPartialTextThrottleTest {

    @Test
    void firstNonBlankTextAlwaysPublishes() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish("hello", 0L)).isTrue();
    }

    @Test
    void blankOrNullNeverPublishes() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish(null, 1_000L)).isFalse();
        assertThat(throttle.shouldPublish("", 1_000L)).isFalse();
        assertThat(throttle.shouldPublish("   ", 1_000L)).isFalse();
    }

    /** A rapid token stream inside the interval is held back. */
    @Test
    void aSecondSmallDeltaWithinTheIntervalIsSuppressed() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish("hello", 1_000L)).isTrue();
        assertThat(throttle.shouldPublish("hello there", 1_050L)).isFalse();
    }

    @Test
    void publishesAgainOnceTheIntervalElapsed() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish("hello", 1_000L)).isTrue();
        assertThat(throttle.shouldPublish("hello there",
                1_000L + TurPartialTextThrottle.MIN_INTERVAL_MS)).isTrue();
    }

    /** A burst must not be held back by the clock — volume is the other trigger. */
    @Test
    void aLargeBurstPublishesEvenInsideTheInterval() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish("x", 1_000L)).isTrue();
        String burst = "x".repeat(TurPartialTextThrottle.BURST_CHARS + 1);
        assertThat(throttle.shouldPublish(burst, 1_010L)).isTrue();
    }

    /**
     * Snapshots can be observed out of order; a shorter buffer must never rewind
     * what the reader already saw.
     */
    @Test
    void neverPublishesAShorterOrEqualBuffer() {
        TurPartialTextThrottle throttle = new TurPartialTextThrottle();
        assertThat(throttle.shouldPublish("hello there", 1_000L)).isTrue();
        assertThat(throttle.shouldPublish("hello", 5_000L)).isFalse();
        assertThat(throttle.shouldPublish("hello there", 5_000L)).isFalse();
    }
}
