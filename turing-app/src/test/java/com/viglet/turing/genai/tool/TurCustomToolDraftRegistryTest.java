/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.tool.TurCustomToolDraftRegistry.DraftEntry;
import com.viglet.turing.genai.tool.TurCustomToolDraftRegistry.PutResult;

/**
 * Pin tests for the T41 live-preview draft registry. Validates:
 * <ul>
 *   <li>compile-on-put with line/column extraction on syntax errors,</li>
 *   <li>per-{@code (username, toolId)} isolation — drafts don't leak between admins,</li>
 *   <li>TTL-based eviction driven by a controllable {@link Clock},</li>
 *   <li>touch-on-find: an actively-used draft survives past nominal TTL.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCustomToolDraftRegistryTest {

    private static final String OK_SCRIPT = "return 'hello ' + (args.name ?: 'world')";
    private static final String BAD_SCRIPT = "def x = ((( unbalanced";

    @Test
    @DisplayName("put compiles the source and returns a populated DraftEntry")
    void put_compilesAndStoresEntry() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();

        PutResult result = registry.put("alice", "tool-1", OK_SCRIPT);

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.entry().scriptClass()).isNotNull();
        assertThat(result.entry().groovySource()).isEqualTo(OK_SCRIPT);
    }

    @Test
    @DisplayName("put with invalid Groovy returns success=false + line/column")
    void put_invalidSourceReportsLineAndColumn() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();

        PutResult result = registry.put("alice", "tool-1", BAD_SCRIPT);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
        assertThat(result.entry()).isNull();
        // line is captured from the Groovy parser — exact value is implementation-defined,
        // but at minimum it must be reported (non-null) so the editor can highlight.
        assertThat(result.line()).isNotNull();
    }

    @Test
    @DisplayName("find returns the stored entry for a (username, toolId) match")
    void find_returnsStoredDraft() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();
        registry.put("alice", "tool-1", OK_SCRIPT);

        Optional<DraftEntry> found = registry.find("alice", "tool-1");

        assertThat(found).isPresent();
        assertThat(found.get().groovySource()).isEqualTo(OK_SCRIPT);
    }

    @Test
    @DisplayName("find returns empty when the username doesn't match — per-admin isolation")
    void find_isolatedByUsername() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();
        registry.put("alice", "tool-1", OK_SCRIPT);

        assertThat(registry.find("bob", "tool-1")).isEmpty();
        assertThat(registry.find(null, "tool-1")).isEmpty();
    }

    @Test
    @DisplayName("find returns empty when the toolId doesn't match")
    void find_isolatedByToolId() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();
        registry.put("alice", "tool-1", OK_SCRIPT);

        assertThat(registry.find("alice", "tool-2")).isEmpty();
    }

    @Test
    @DisplayName("clear removes the draft and returns true; returns false when nothing to clear")
    void clear_isIdempotent() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();
        registry.put("alice", "tool-1", OK_SCRIPT);

        assertThat(registry.clear("alice", "tool-1")).isTrue();
        assertThat(registry.find("alice", "tool-1")).isEmpty();
        assertThat(registry.clear("alice", "tool-1")).isFalse();
    }

    @Test
    @DisplayName("TTL expires an idle draft; evictExpired sweeps it out")
    void ttl_expiresIdleDraft() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-26T10:00:00Z"));
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry(clock);

        registry.put("alice", "tool-1", OK_SCRIPT);
        assertThat(registry.find("alice", "tool-1")).isPresent();

        // Advance past TTL without touching → find should return empty (lazy
        // eviction on miss), and explicit sweep should reap.
        clock.advance(TurCustomToolDraftRegistry.TTL.plus(Duration.ofMinutes(1)));

        assertThat(registry.find("alice", "tool-1"))
                .as("expired draft should be invisible on find")
                .isEmpty();
        // The previous find already evicted it. Another put to verify size:
        registry.put("alice", "tool-2", OK_SCRIPT);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("touch on find: an actively-used draft survives past nominal TTL")
    void touchOnFind_keepsDraftAlive() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-26T10:00:00Z"));
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry(clock);

        registry.put("alice", "tool-1", OK_SCRIPT);

        // Just under TTL → still live.
        clock.advance(TurCustomToolDraftRegistry.TTL.minus(Duration.ofMinutes(1)));
        assertThat(registry.find("alice", "tool-1")).isPresent(); // touches lastTouched

        // Advance close-to-TTL again from the touch point → still alive
        // because the touch reset the clock.
        clock.advance(TurCustomToolDraftRegistry.TTL.minus(Duration.ofMinutes(1)));
        assertThat(registry.find("alice", "tool-1"))
                .as("an active editor's draft should survive across multiple TTLs as long as it's touched")
                .isPresent();
    }

    @Test
    @DisplayName("evictExpired returns the count of reaped entries")
    void evictExpired_returnsCount() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-26T10:00:00Z"));
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry(clock);

        registry.put("alice", "tool-1", OK_SCRIPT);
        registry.put("bob", "tool-2", OK_SCRIPT);
        registry.put("alice", "tool-3", OK_SCRIPT);

        clock.advance(TurCustomToolDraftRegistry.TTL.plus(Duration.ofMinutes(1)));

        assertThat(registry.evictExpired()).isEqualTo(3);
        assertThat(registry.size()).isZero();
    }

    @Test
    @DisplayName("put with blank username is rejected")
    void put_requiresUsername() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();

        PutResult result = registry.put("  ", "tool-1", OK_SCRIPT);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("authenticated");
    }

    @Test
    @DisplayName("put with blank toolId is rejected")
    void put_requiresToolId() {
        TurCustomToolDraftRegistry registry = new TurCustomToolDraftRegistry();

        PutResult result = registry.put("alice", "", OK_SCRIPT);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("toolId");
    }

    /**
     * Mutable clock for TTL tests — Clock.fixed is immutable; we need to
     * advance it across the test to drive eviction deterministically without
     * sleeps.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = this.now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }
    }
}
