/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.capture;

import java.util.List;

/**
 * T618 / §XXXIV.6 — the exact assembled prompt captured for one conversation
 * turn at send time, as it is serialized onto the storage seam.
 *
 * <p>This is the verbatim ground truth the Live Preview replays: the {@code
 * systemPrompt} is the literal system message the model received and {@code
 * messages} is the T617 whole-message list (system + history + the turn's user
 * message), in send order. Unlike a T612 current-state replay (which re-runs the
 * shared assembler over the conversation's <em>current</em> persisted state),
 * this is a snapshot frozen at the moment the executor handed the list to the
 * LLM, so it survives later flow advances, slot overwrites, and memory
 * compaction.
 *
 * @param conversationId the owning conversation.
 * @param turnIndex      1-based turn number within the conversation (the count of
 *                       user messages in the assembled list at send time).
 * @param capturedAt     epoch millis when the turn was captured.
 * @param agentId        the agent that assembled the prompt.
 * @param systemPrompt   the literal system message text sent to the model.
 * @param messages       the whole message list, in send order (system first).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptCapture(
        String conversationId,
        int turnIndex,
        long capturedAt,
        String agentId,
        String systemPrompt,
        List<Message> messages) {

    public TurPromptCapture {
        messages = messages == null ? List.of() : messages;
    }

    /**
     * One message of the captured list.
     *
     * @param role    {@code "system"}, {@code "user"}, or {@code "assistant"}.
     * @param content the literal message text.
     */
    public record Message(String role, String content) {
    }
}
