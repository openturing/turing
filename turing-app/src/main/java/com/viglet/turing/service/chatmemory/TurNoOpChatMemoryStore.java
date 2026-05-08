package com.viglet.turing.service.chatmemory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Override
    public List<Map<String, Object>> findMessages(String conversationId, int limit) {
        return Collections.emptyList();
    }
}
