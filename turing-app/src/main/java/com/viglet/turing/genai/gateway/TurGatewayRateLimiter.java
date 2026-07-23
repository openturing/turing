/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.spring.security.abuse.TurFixedWindowRateLimiter;

/**
 * T742 / §XLIX — per-virtual-key request rate limiting for the Governed LLM
 * Gateway, reusing the dependency-free fixed-window counter
 * ({@link TurFixedWindowRateLimiter}) that backs the anonymous-abuse filter.
 *
 * <p>Each key gets its own limiter, sized from {@code TurGatewayKey.getRateLimitPerMinute()}
 * over a 60s window; a key with no (or {@code <=0}) limit is unlimited. A limiter
 * is rebuilt when a key's configured limit changes, so an admin edit takes effect
 * without a restart.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurGatewayRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private record Entry(int max, TurFixedWindowRateLimiter limiter) {
    }

    private final ConcurrentHashMap<String, Entry> limiters = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the request is allowed, {@code false} once the key's
     *         per-minute limit is exceeded. Unlimited (always true) when the key
     *         has no positive {@code rateLimitPerMinute}.
     */
    public boolean tryAcquire(TurGatewayKey key) {
        if (key == null || key.getId() == null) {
            return true;
        }
        Integer perMinute = key.getRateLimitPerMinute();
        if (perMinute == null || perMinute <= 0) {
            limiters.remove(key.getId());
            return true;
        }
        int max = perMinute;
        Entry entry = limiters.compute(key.getId(), (id, existing) ->
                (existing != null && existing.max() == max)
                        ? existing
                        : new Entry(max, new TurFixedWindowRateLimiter(max, WINDOW_MILLIS)));
        return entry.limiter().tryAcquire(key.getId());
    }
}
