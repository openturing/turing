package com.viglet.turing.service.chatmemory;

import java.util.List;

/**
 * Pluggable persistence for chat memory. Each call writes a batch of events
 * for the same {@code conversationId} as a single round-trip; the store is
 * responsible for trimming older messages past {@code maxMessages} and for
 * keeping the document's headline fields ({@code lastUserMessage},
 * {@code lastAssistantMessage}, {@code turnCount}, {@code updatedAt}) in sync.
 *
 * <p>Implementations are picked at startup by {@link TurChatMemoryConfig}
 * based on {@code turing.logging.engine}. The NoOp implementation is always
 * available and is the default when the engine is set to {@code none}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurChatMemoryStore {

    /** Whether the store is wired up and writes will be persisted. */
    boolean isEnabled();

    /** Storage backend identifier — useful for diagnostics. */
    TurChatMemoryEngine getEngine();

    /**
     * Persists a batch of {@link TurChatMemoryEvent} for the same
     * conversation. Caller guarantees the list is non-empty and that every
     * event in the batch shares the same {@code conversationId}.
     */
    void appendBatch(String conversationId, List<TurChatMemoryEvent> batch);
}
