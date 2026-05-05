package com.viglet.turing.service.chatmemory;

import java.time.Instant;

/**
 * One immutable chat turn captured by {@link TurChatMemoryService} for later
 * persistence. Always carries both the user query and the assistant reply
 * because the SDK guarantees they're produced in lockstep — there is no
 * concept of a half-recorded turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurChatMemoryEvent(
        String siteName,
        String agentId,
        String conversationId,
        String locale,
        String userMessage,
        String assistantMessage,
        Instant timestamp,
        int maxMessages) {
}
