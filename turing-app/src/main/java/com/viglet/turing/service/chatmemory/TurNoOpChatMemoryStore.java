package com.viglet.turing.service.chatmemory;

import java.util.List;

/**
 * Default implementation when {@code turing.logging.engine == none} or the
 * configured engine is unavailable. Drops every batch silently — callers
 * should still gate on {@link #isEnabled()} to avoid building events that
 * will never be written.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurNoOpChatMemoryStore implements TurChatMemoryStore {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public TurChatMemoryEngine getEngine() {
        return TurChatMemoryEngine.NONE;
    }

    @Override
    public void appendBatch(String conversationId, List<TurChatMemoryEvent> batch) {
        // intentionally empty
    }
}
