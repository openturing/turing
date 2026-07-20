/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;

/**
 * T742 / §XLIX — unit coverage for per-key gateway rate limiting.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurGatewayRateLimiterTest {

    private TurGatewayKey key(String id, Integer perMinute) {
        TurGatewayKey k = new TurGatewayKey();
        k.setId(id);
        k.setRateLimitPerMinute(perMinute);
        return k;
    }

    @Test
    void unlimitedWhenNoLimitConfigured() {
        TurGatewayRateLimiter limiter = new TurGatewayRateLimiter();
        TurGatewayKey k = key("k1", null);
        for (int i = 0; i < 1000; i++) {
            assertThat(limiter.tryAcquire(k)).isTrue();
        }
    }

    @Test
    void blocksOncePerMinuteLimitReached() {
        TurGatewayRateLimiter limiter = new TurGatewayRateLimiter();
        TurGatewayKey k = key("k2", 3);

        assertThat(limiter.tryAcquire(k)).isTrue();
        assertThat(limiter.tryAcquire(k)).isTrue();
        assertThat(limiter.tryAcquire(k)).isTrue();
        assertThat(limiter.tryAcquire(k)).isFalse();
    }

    @Test
    void limitsAreIndependentPerKey() {
        TurGatewayRateLimiter limiter = new TurGatewayRateLimiter();
        TurGatewayKey a = key("a", 1);
        TurGatewayKey b = key("b", 1);

        assertThat(limiter.tryAcquire(a)).isTrue();
        assertThat(limiter.tryAcquire(a)).isFalse();
        // b has its own bucket.
        assertThat(limiter.tryAcquire(b)).isTrue();
    }

    @Test
    void nullKeyIsAllowed() {
        assertThat(new TurGatewayRateLimiter().tryAcquire(null)).isTrue();
    }
}
