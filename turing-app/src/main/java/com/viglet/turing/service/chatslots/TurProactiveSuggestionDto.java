/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

/**
 * T445 / §XXIII.4 — one proactive copilot offer pushed over
 * {@code /chat/proactive/stream}.
 *
 * @param conversationId  the conversation the offer belongs to
 * @param signal          the ambient-signal kind that triggered it (the slot name
 *                        without the {@code signal.} prefix, e.g. {@code return_policy})
 * @param count           the interaction count that crossed the threshold
 * @param message         a ready-to-show nudge the host can render verbatim
 * @param suggestedPrompt the message to send to the chat if the user accepts
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurProactiveSuggestionDto(
        String conversationId,
        String signal,
        int count,
        String message,
        String suggestedPrompt) {
}
