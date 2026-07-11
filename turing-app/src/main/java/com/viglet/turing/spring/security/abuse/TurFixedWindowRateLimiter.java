/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.abuse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * T641 / §XXXVII.3 — a small, dependency-free fixed-window rate limiter used by
 * {@link TurAnonymousAbuseRateLimitFilter} to throttle the anonymous public
 * chat / search surface per client IP and per session.
 *
 * <p>Each key gets a counter bucketed into fixed windows of {@code windowMillis}.
 * {@link #tryAcquire(String)} increments the current window's counter and returns
 * {@code false} once it exceeds {@code maxPerWindow}. State is held in a bounded
 * {@link ConcurrentHashMap}; entries whose window has rolled over are reset
 * lazily on access, and the map is capped so a flood of distinct keys cannot
 * grow it without bound (once at the cap, unknown keys are allowed through so a
 * spoofed-key flood degrades to "no limiting" rather than blocking everyone).
 *
 * <p>The clock is injected as a {@link LongSupplier} so tests can advance time
 * deterministically.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurFixedWindowRateLimiter {

    /** Hard cap on distinct tracked keys to bound memory under a key-spraying flood. */
    private static final int MAX_KEYS = 100_000;

    private final int maxPerWindow;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public TurFixedWindowRateLimiter(int maxPerWindow, long windowMillis) {
        this(maxPerWindow, windowMillis, System::currentTimeMillis);
    }

    public TurFixedWindowRateLimiter(int maxPerWindow, long windowMillis, LongSupplier clock) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis <= 0 ? 1L : windowMillis;
        this.clock = clock;
    }

    /**
     * Records one hit for {@code key} in the current window.
     *
     * @return {@code true} if the request is within the limit, {@code false} if
     *         the limit for the current window is exceeded. Always {@code true}
     *         when {@code maxPerWindow <= 0} (dimension disabled).
     */
    public boolean tryAcquire(String key) {
        if (maxPerWindow <= 0 || key == null) {
            return true;
        }
        long now = clock.getAsLong();
        long currentWindow = now / windowMillis;

        // Bound memory: once at the cap, don't track new keys (fail-open).
        if (!windows.containsKey(key) && windows.size() >= MAX_KEYS) {
            return true;
        }

        Window window = windows.computeIfAbsent(key, k -> new Window(currentWindow));
        synchronized (window) {
            if (window.windowId != currentWindow) {
                window.windowId = currentWindow;
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= maxPerWindow;
        }
    }

    /** Removes counters whose window is older than the current one — cheap periodic hygiene. */
    public void evictStale() {
        long currentWindow = clock.getAsLong() / windowMillis;
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().windowId < currentWindow && e.getValue().count.get() == 0
                        || e.getValue().windowId < currentWindow - 1;
            }
        });
    }

    int trackedKeys() {
        return windows.size();
    }

    private static final class Window {
        private long windowId;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long windowId) {
            this.windowId = windowId;
        }
    }
}
