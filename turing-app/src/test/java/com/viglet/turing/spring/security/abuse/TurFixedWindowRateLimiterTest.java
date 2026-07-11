/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * T641 / §XXXVII.3 — unit tests for the fixed-window rate limiter.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurFixedWindowRateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocksWithinWindow() {
        var limiter = new TurFixedWindowRateLimiter(3, 1000L, () -> 0L);

        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.tryAcquire("ip")).isFalse();
    }

    @Test
    void resetsCounterOnNewWindow() {
        AtomicLong now = new AtomicLong(0L);
        var limiter = new TurFixedWindowRateLimiter(2, 1000L, now::get);

        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();

        now.set(1000L); // next window
        assertThat(limiter.tryAcquire("ip")).isTrue();
    }

    @Test
    void tracksKeysIndependently() {
        var limiter = new TurFixedWindowRateLimiter(1, 1000L, () -> 0L);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void disabledDimensionAlwaysAllows() {
        var limiter = new TurFixedWindowRateLimiter(0, 1000L, () -> 0L);

        for (int i = 0; i < 1000; i++) {
            assertThat(limiter.tryAcquire("ip")).isTrue();
        }
    }

    @Test
    void nullKeyAllowed() {
        var limiter = new TurFixedWindowRateLimiter(1, 1000L, () -> 0L);
        assertThat(limiter.tryAcquire(null)).isTrue();
    }

    @Test
    void evictStaleRemovesOldWindows() {
        AtomicLong now = new AtomicLong(0L);
        var limiter = new TurFixedWindowRateLimiter(5, 1000L, now::get);

        limiter.tryAcquire("ip");
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        now.set(3000L); // two windows later
        limiter.evictStale();
        assertThat(limiter.trackedKeys()).isZero();
    }
}
