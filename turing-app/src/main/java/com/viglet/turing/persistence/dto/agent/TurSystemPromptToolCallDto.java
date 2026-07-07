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
 * Block AK / T612 — one tool invocation from a replayed conversation's T427
 * tool-call trace, shown alongside the assembled prompt so the operator sees
 * <em>what the model actually did</em> on that conversation (not just what it
 * was told). Mirrors {@link com.viglet.turing.genai.tool.TurChatToolCall}'s
 * terminal shape, trimmed to the fields the preview renders.
 *
 * @param name        the tool name (matches the schema channel entry).
 * @param argsSummary the redacted/truncated argument digest — never raw secrets.
 * @param status      {@code "ok"} | {@code "error"}.
 * @param durationMs  end-to-end call duration in milliseconds.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSystemPromptToolCallDto(
        String name,
        String argsSummary,
        String status,
        long durationMs) {
}
