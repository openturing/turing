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

/**
 * T849 / §LXIII.4 — decides how often an arriving transcript is worth pushing
 * over SSE. A streaming backend emits a delta every few tokens; republishing a
 * full job snapshot per delta would flood the bus (and every subscriber) for no
 * perceptible gain, since a reader cannot follow text that repaints 30× a second.
 *
 * <p>The policy is deliberately two-sided: publish when <b>enough time</b> has
 * passed (so slow, sparse speech still updates) <b>or</b> when <b>enough text</b>
 * has accumulated (so a fast burst is not held back by the clock). The final
 * transcript is never throttled — it rides the terminal snapshot, which is
 * published unconditionally.
 *
 * <p>Not thread-safe by itself; callers hold the job monitor while consulting it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurPartialTextThrottle {

    /** Minimum gap between two partial publishes. */
    static final long MIN_INTERVAL_MS = 400L;

    /** Characters that force a publish regardless of the interval. */
    static final int BURST_CHARS = 200;

    private boolean published;
    private long lastPublishedAt;
    private int lastPublishedLength;

    /**
     * Whether {@code text} should be published now.
     *
     * @param text the transcript accumulated so far
     * @param now  current epoch millis (injected so the policy is testable)
     * @return {@code true} when the caller should publish; the throttle then
     *         records this publish and the next one has to earn its own slot
     */
    public boolean shouldPublish(String text, long now) {
        if (text == null || text.isBlank()) {
            return false;
        }
        int length = text.length();
        if (length <= lastPublishedLength) {
            // Never rewind: a shorter/equal buffer means nothing new to show.
            return false;
        }
        // The first partial always goes out: it is what turns a bare spinner into
        // visible progress, and waiting out an interval to show it defeats the point.
        boolean dueByTime = !published || now - lastPublishedAt >= MIN_INTERVAL_MS;
        boolean dueByVolume = length - lastPublishedLength >= BURST_CHARS;
        if (!dueByTime && !dueByVolume) {
            return false;
        }
        published = true;
        lastPublishedAt = now;
        lastPublishedLength = length;
        return true;
    }
}
