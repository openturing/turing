/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * Block AL / §XXXV.3 (T617) — one history-prefix message in the AI Agent
 * "System Prompt" page Live Preview. Where a {@link TurSystemPromptSegmentDto}
 * is a fragment of the single <em>system</em> message, this is a whole
 * conversational turn ({@code user}/{@code assistant}) the runtime prepends to
 * the client history — the T115 chat-memory summary and the T30
 * relevance-retrieved older turns.
 *
 * <p>Mirrors {@link com.viglet.turing.genai.prompt.TurPromptMessage} so the
 * preview renders the <em>same</em> messages the runtime
 * {@code TurMessageAssemblyPipeline} emits (no drift). A config-level preview has
 * no live conversation, so these are surfaced as {@code runtimeOnly}
 * placeholders explaining when each source materializes; when a conversation is
 * threaded through, the concrete message body is carried verbatim.
 *
 * @param role        {@code "user"} or {@code "assistant"} — the turn type the
 *                    runtime builds.
 * @param origin      stable machine-readable source bucket the frontend themes
 *                    on: {@code MEMORY_SUMMARY} or {@code MEMORY_RELEVANCE}
 *                    (the {@code TurPromptMessage.ORIGIN_*} constants).
 * @param label       human-readable name for the message.
 * @param content     the literal message body, or empty for an informational
 *                    {@code runtimeOnly} placeholder.
 * @param tokens      best-effort {@code chars/4} token estimate for
 *                    {@code content} (0 for placeholders).
 * @param runtimeOnly {@code true} when the message only materializes once the
 *                    conversation accumulates history beyond the recent window —
 *                    shown as an example, not fixed text.
 * @param note        one-line explanation of when/why the message appears;
 *                    {@code null} when self-explanatory.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSystemPromptMessageDto(
        String role,
        String origin,
        String label,
        String content,
        int tokens,
        boolean runtimeOnly,
        String note) {
}
