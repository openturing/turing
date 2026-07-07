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

/**
 * A single tool-call lifecycle record shared by two surfaces:
 *
 * <ul>
 *   <li><b>T436 live SSE event</b> — emitted as a {@code "tool_call"} event on
 *       the chat stream so the UI can show "the agent is calling
 *       {@code search_knowledge_base}…" while the server-side tool loop runs.
 *       The {@code start} phase carries the (redacted) {@link #argsSummary};
 *       the {@code end} phase carries {@link #status} + {@link #durationMs}.</li>
 *   <li><b>T427 read-only trace</b> — the completed calls accumulate per
 *       conversation and are exposed at
 *       {@code GET /api/system/chat-analytics/sessions/{id}/tool-calls} so
 *       {@code turing eval}'s {@code assert.tool_called} is exact (not inferred
 *       from slot-audit {@code TOOL} writes). Stored as the {@code end} phase
 *       with both {@code argsSummary} and {@code status}/{@code durationMs}.</li>
 * </ul>
 *
 * <p>Both surfaces agree on this one shape so a live event and the read-only
 * trace describe the same invocation identically.
 *
 * @param callId      unique id of this invocation within the turn
 * @param name        tool name (matches Spring AI's {@code ToolDefinition.name()})
 * @param phase       {@code "start"} | {@code "end"}
 * @param argsSummary redacted/truncated argument digest — never raw secrets
 *                    (present on {@code start} and on the persisted trace entry)
 * @param status      {@code "ok"} | {@code "error"} (present on {@code end})
 * @param durationMs  end-to-end call duration (present on {@code end})
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurChatToolCall(
        String callId,
        String name,
        String phase,
        String argsSummary,
        String status,
        Long durationMs) {

    /** Phase marker emitted the instant a tool invocation begins. */
    public static TurChatToolCall start(String callId, String name, String argsSummary) {
        return new TurChatToolCall(callId, name, "start", argsSummary, null, null);
    }

    /** Live {@code end} event — status + duration, no args (already sent on start). */
    public static TurChatToolCall end(String callId, String name, boolean ok, long durationMs) {
        return new TurChatToolCall(callId, name, "end", null, ok ? "ok" : "error", durationMs);
    }

    /**
     * Fully-populated terminal record kept in the read-only trace: carries the
     * args digest <em>and</em> the outcome so a single entry fully describes the
     * invocation independent of the live {@code start} event.
     */
    public static TurChatToolCall completed(String callId, String name, String argsSummary,
            boolean ok, long durationMs) {
        return new TurChatToolCall(callId, name, "end", argsSummary, ok ? "ok" : "error", durationMs);
    }
}
