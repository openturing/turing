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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-turn sink the {@link TurLoggingToolCallback} writes a {@link TurChatToolCall}
 * into as each tool runs. Mirrors the {@code TurRagSourceCollector} pattern: a
 * mutable object placed in the prompt's tool-context map
 * ({@link TurCustomToolCallbackService#TOOL_CONTEXT_TOOL_CALLS}) and carried by
 * reference across every {@code TurToolExecutionLoop} round, so the same
 * collector accumulates the whole turn's tool activity.
 *
 * <p>Two consumers read it:
 * <ul>
 *   <li>after the turn, the streaming dispatcher drains {@link #snapshot()} into
 *       the per-conversation trace store (T427);</li>
 *   <li>while the turn runs, an optional {@linkplain #attachListener(Consumer)
 *       live listener} (attached only when the agent opts into
 *       {@code toolCallEventsEnabled}) forwards each {@code start}/{@code end}
 *       phase onto the chat SSE (T436).</li>
 * </ul>
 *
 * <p>Recording is synchronized so concurrent tool callbacks within a turn (rare
 * — Spring AI serializes them) don't corrupt the list. The live listener is
 * invoked outside the lock and any throw is swallowed: tool-call observability
 * must never break the tool itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurToolCallCollector {

    private final List<TurChatToolCall> completed =
            Collections.synchronizedList(new ArrayList<>());

    /** Live SSE forwarder; null until/unless the agent opts into live events. */
    private volatile Consumer<TurChatToolCall> listener;

    /**
     * Attach the live forwarder. Only the dispatcher calls this, once, before
     * the tool loop runs — and only when {@code toolCallEventsEnabled} is on.
     */
    public void attachListener(Consumer<TurChatToolCall> listener) {
        this.listener = listener;
    }

    /** Tool invocation begins — emits a live {@code start} event (no trace row yet). */
    public void onStart(String callId, String name, String argsSummary) {
        emit(TurChatToolCall.start(callId, name, argsSummary));
    }

    /**
     * Tool invocation completed (or threw) — records the terminal trace row and
     * emits a live {@code end} event.
     */
    public void onEnd(String callId, String name, String argsSummary, boolean ok, long durationMs) {
        completed.add(TurChatToolCall.completed(callId, name, argsSummary, ok, durationMs));
        emit(TurChatToolCall.end(callId, name, ok, durationMs));
    }

    private void emit(TurChatToolCall event) {
        Consumer<TurChatToolCall> l = this.listener;
        if (l == null) {
            return;
        }
        try {
            l.accept(event);
        } catch (RuntimeException ignored) {
            // best-effort live event — never break the tool loop on a sink error
        }
    }

    /** Immutable copy of the terminal tool-call rows recorded this turn. */
    public List<TurChatToolCall> snapshot() {
        synchronized (completed) {
            return List.copyOf(completed);
        }
    }

    /** True when no tool ran this turn — lets callers skip empty trace writes. */
    public boolean isEmpty() {
        synchronized (completed) {
            return completed.isEmpty();
        }
    }
}
