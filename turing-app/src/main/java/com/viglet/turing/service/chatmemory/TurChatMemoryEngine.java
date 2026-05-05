package com.viglet.turing.service.chatmemory;

/**
 * Storage backend selected by {@code turing.logging.engine}. Drives which
 * {@link TurChatMemoryStore} implementation gets the bean.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public enum TurChatMemoryEngine {
    NONE,
    MONGODB,
    REDIS;

    public static TurChatMemoryEngine fromConfig(String value) {
        if (value == null || value.isBlank()) return NONE;
        return switch (value.trim().toLowerCase()) {
            case "mongodb" -> MONGODB;
            case "redis" -> REDIS;
            default -> NONE;
        };
    }
}
