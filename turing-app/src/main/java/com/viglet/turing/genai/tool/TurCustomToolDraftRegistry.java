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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-admin overlay of Groovy source for Custom Tools. Powers T41 — the
 * "live unsaved-script preview" feature: while an admin is iterating on a
 * Custom Tool, they can push the in-editor draft to the backend and have
 * their own chat conversations execute that draft instead of the persisted
 * version, without saving (which would affect every visitor).
 *
 * <p>Each entry is keyed by {@code (username, toolId)} so two admins can
 * iterate concurrently on the same tool without colliding, and the same
 * admin can have drafts on multiple tools. Visitors and chat sessions
 * authenticated as anyone OTHER than the draft owner see the persisted
 * tool, unchanged — there is no chance of a half-baked script leaking
 * into production traffic.
 *
 * <p><b>Compilation upfront.</b> {@link #put(String, String, String)}
 * compiles the supplied Groovy source synchronously and rejects invalid
 * payloads with the line/column extracted from the Groovy parser. The
 * compiled {@code Class} is cached on the entry so {@link #find(String, String)}
 * returns ready-to-instantiate state — same shape as
 * {@link TurCustomToolCallbackService}'s {@code CachedScript}.
 *
 * <p><b>TTL.</b> Drafts auto-expire after {@link #TTL} of inactivity (touched
 * on every {@link #find(String, String)} and {@link #put}); a background
 * sweep evicts stale entries every {@link #SWEEP_INTERVAL_MS} ms. Keeps the
 * map bounded even if an admin closes their tab without explicitly clearing.
 * Eviction is observable via {@link #evictExpired()} for unit tests that
 * want to deterministically advance the clock.
 *
 * <p><b>Single-JVM scope.</b> Drafts are not replicated across cluster
 * nodes. Typical deploys use session affinity so an admin's traffic stays
 * on one JVM; if they fail over mid-iteration, the draft is lost and the
 * editor falls back to the persisted source — annoying but not unsafe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurCustomToolDraftRegistry {

    /** Time after which an idle draft is reaped. */
    static final Duration TTL = Duration.ofMinutes(30);

    /** Interval between scheduled cleanup runs. */
    static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000L;

    private final Clock clock;
    private final ConcurrentHashMap<DraftKey, DraftEntry> drafts = new ConcurrentHashMap<>();
    /** Monotonic counter for cleanup observability — tested via {@link #getEvictedCount()}. */
    private final AtomicInteger evictedCount = new AtomicInteger();

    @Autowired
    public TurCustomToolDraftRegistry() {
        this(Clock.systemUTC());
    }

    /** Package-private constructor for tests that want to drive a fake clock. */
    TurCustomToolDraftRegistry(Clock clock) {
        this.clock = clock;
    }

    /**
     * Compiles {@code groovySource} and stores it as the active draft for
     * {@code (username, toolId)}. Replaces any previous draft for the same
     * key. Returns the parsed entry on success.
     *
     * @throws PutResult.CompileException when the source does not parse —
     *         the exception carries the line and column extracted from the
     *         Groovy compiler so the frontend can highlight the bad spot.
     */
    public PutResult put(String username, String toolId, String groovySource) {
        if (username == null || username.isBlank()) {
            return PutResult.error("draft requires authenticated user", null, null);
        }
        if (toolId == null || toolId.isBlank()) {
            return PutResult.error("toolId is required", null, null);
        }
        if (groovySource == null) {
            groovySource = "";
        }
        try {
            String sanitized = TurCustomToolCallbackService.sanitizeGroovySource(groovySource);
            GroovyShell shell = new GroovyShell();
            @SuppressWarnings("unchecked")
            Class<? extends Script> scriptClass = shell.getClassLoader()
                    .parseClass(sanitized, "TurCustomToolDraft_" + toolId + ".groovy");
            DraftEntry entry = new DraftEntry(groovySource, scriptClass,
                    clock.instant(), clock.instant());
            drafts.put(new DraftKey(username, toolId), entry);
            log.info("[CustomTool/draft] stored draft username='{}' toolId='{}' ({} chars)",
                    username, toolId, groovySource.length());
            return PutResult.ok(entry);
        } catch (MultipleCompilationErrorsException e) {
            var firstSyntax = e.getErrorCollector().getErrors().stream()
                    .filter(org.codehaus.groovy.control.messages.SyntaxErrorMessage.class::isInstance)
                    .map(org.codehaus.groovy.control.messages.SyntaxErrorMessage.class::cast)
                    .findFirst()
                    .map(org.codehaus.groovy.control.messages.SyntaxErrorMessage::getCause);
            return firstSyntax.map(sx -> PutResult.error(sx.getMessage(),
                            sx.getStartLine() > 0 ? sx.getStartLine() : null,
                            sx.getStartColumn() > 0 ? sx.getStartColumn() : null))
                    .orElseGet(() -> PutResult.error(e.getMessage(), null, null));
        } catch (Exception e) {
            return PutResult.error(e.getMessage(), null, null);
        }
    }

    /**
     * Returns the active draft for {@code (username, toolId)} if one exists
     * and has not expired. Touches the entry on hit so an active editor
     * keeps the draft alive even past the static TTL.
     */
    public Optional<DraftEntry> find(String username, String toolId) {
        if (username == null || toolId == null) {
            return Optional.empty();
        }
        DraftKey key = new DraftKey(username, toolId);
        DraftEntry entry = drafts.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, clock.instant())) {
            // Defensive remove — the background sweep should catch it too.
            drafts.remove(key, entry);
            evictedCount.incrementAndGet();
            return Optional.empty();
        }
        // Touch so an actively-used draft survives even past nominal TTL.
        DraftEntry touched = entry.withLastTouched(clock.instant());
        drafts.replace(key, entry, touched);
        return Optional.of(touched);
    }

    /** Removes the draft for {@code (username, toolId)}. Returns {@code true} when one was present. */
    public boolean clear(String username, String toolId) {
        if (username == null || toolId == null) {
            return false;
        }
        return drafts.remove(new DraftKey(username, toolId)) != null;
    }

    /**
     * Evicts expired drafts. Invoked on a fixed schedule; exposed as
     * package-private (via {@link #evictExpired()}) so tests can advance a
     * fake clock and assert deterministic eviction.
     */
    @Scheduled(fixedRate = SWEEP_INTERVAL_MS, initialDelay = SWEEP_INTERVAL_MS)
    public void scheduledEvict() {
        int evicted = evictExpired();
        if (evicted > 0) {
            log.info("[CustomTool/draft] swept {} expired draft(s); {} remain", evicted, drafts.size());
        }
    }

    int evictExpired() {
        Instant now = clock.instant();
        int count = 0;
        for (var e : drafts.entrySet()) {
            if (isExpired(e.getValue(), now) && drafts.remove(e.getKey(), e.getValue())) {
                count++;
            }
        }
        evictedCount.addAndGet(count);
        return count;
    }

    int getEvictedCount() {
        return evictedCount.get();
    }

    int size() {
        return drafts.size();
    }

    private static boolean isExpired(DraftEntry entry, Instant now) {
        return Duration.between(entry.lastTouched(), now).compareTo(TTL) > 0;
    }

    public record DraftKey(String username, String toolId) {
    }

    /**
     * A stored draft — source kept verbatim for round-tripping back to the
     * editor on page reload, compiled class kept for cheap re-instantiation
     * across many chat turns.
     */
    public record DraftEntry(String groovySource,
            Class<? extends Script> scriptClass,
            Instant lastTouched,
            Instant createdAt) {

        DraftEntry withLastTouched(Instant newTouched) {
            return new DraftEntry(groovySource, scriptClass, newTouched, createdAt);
        }
    }

    /**
     * Result envelope returned by {@link #put(String, String, String)}.
     * Always populates {@code success}; on failure {@code error} carries a
     * human-readable message and {@code line}/{@code column} point at the
     * offending Groovy token when extractable. On success the {@code entry}
     * is populated so callers can echo creation metadata back to the
     * frontend.
     */
    public record PutResult(boolean success, DraftEntry entry, String error,
            Integer line, Integer column) {

        static PutResult ok(DraftEntry entry) {
            return new PutResult(true, entry, null, null, null);
        }

        static PutResult error(String message, Integer line, Integer column) {
            return new PutResult(false, null, message, line, column);
        }

        /** Thrown by callers that prefer exceptional flow over the envelope. */
        public static class CompileException extends RuntimeException {
            private static final long serialVersionUID = 1L;
            public final Integer line;
            public final Integer column;

            public CompileException(String message, Integer line, Integer column) {
                super(message);
                this.line = line;
                this.column = column;
            }
        }
    }
}
