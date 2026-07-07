/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.tool.TurChatToolCall;

import lombok.extern.slf4j.Slf4j;

/**
 * T427 — bounded, in-memory per-conversation tool-call trace.
 *
 * <p>{@code turing eval}'s {@code assert.tool_called} used to be best-effort:
 * it inferred a tool fired by scanning the T60 slot-audit for {@code source=TOOL}
 * writes, so a tool that ran without writing a slot was invisible and a slot
 * written by two paths was ambiguous. This store records the tools that
 * <em>actually executed</em> per conversation (name, arg digest, latency,
 * ok/err) so the assertion is exact, and doubles as a per-conversation tool
 * timeline for observability.
 *
 * <p>The data is captured by {@link com.viglet.turing.genai.tool.TurLoggingToolCallback}
 * into a per-turn {@code TurToolCallCollector} and drained here once the turn
 * completes. Storage is process-local (like the in-flight analytics bag) and
 * bounded two ways so a chatty or long-lived deployment can't grow it without
 * limit: an access-ordered LRU caps the number of tracked conversations, and a
 * ring per conversation caps retained calls.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurToolCallTraceService {

    /** Max distinct conversations retained; least-recently-touched is evicted. */
    static final int MAX_CONVERSATIONS = 500;
    /** Max tool calls retained per conversation; oldest dropped past the cap. */
    static final int MAX_CALLS_PER_CONVERSATION = 200;

    private final Map<String, Deque<TurChatToolCall>> traces = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Deque<TurChatToolCall>> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    /**
     * Append a turn's completed tool calls to the conversation's trace.
     * No-op for a blank id or an empty batch.
     */
    public void record(String conversationId, List<TurChatToolCall> calls) {
        if (conversationId == null || conversationId.isBlank() || calls == null || calls.isEmpty()) {
            return;
        }
        synchronized (traces) {
            Deque<TurChatToolCall> dq = traces.computeIfAbsent(conversationId, k -> new ArrayDeque<>());
            for (TurChatToolCall call : calls) {
                dq.addLast(call);
                while (dq.size() > MAX_CALLS_PER_CONVERSATION) {
                    dq.pollFirst();
                }
            }
        }
        log.debug("[ToolTrace] conv={} recorded {} tool call(s)", conversationId, calls.size());
    }

    /**
     * @return an immutable, chronological copy of the tool calls recorded for
     *         {@code conversationId}, or an empty list when none are tracked.
     */
    public List<TurChatToolCall> getTrace(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        synchronized (traces) {
            Deque<TurChatToolCall> dq = traces.get(conversationId);
            return dq == null ? List.of() : List.copyOf(dq);
        }
    }

    /** Drop a conversation's trace (e.g. when its session is purged). */
    public void evict(String conversationId) {
        if (conversationId == null) {
            return;
        }
        synchronized (traces) {
            traces.remove(conversationId);
        }
    }
}
