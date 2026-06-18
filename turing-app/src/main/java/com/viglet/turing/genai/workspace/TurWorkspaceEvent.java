/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

/**
 * A single workspace mutation broadcast on the {@link TurWorkspaceEventBus}
 * (T113). Mirrors {@code TurChatSessionSlotsDto} for the slot bus, but carries
 * only <b>metadata</b> — never the blob bytes.
 *
 * <p>This is the deliberate difference from the slot bus: a slot event re-pushes
 * the whole (small) value map, while a workspace blob can be tens of megabytes.
 * Re-broadcasting the bytes on every change would saturate every connected SSE
 * client, so a workspace event advertises only {@code {event, key, contentType,
 * size, signedUrl}}. A consumer that wants the bytes follows {@code signedUrl}
 * (a time-limited, HMAC-signed {@code /api/v2/workspace/file} link).
 *
 * <p>{@link #conversationId} is the routing key the bus filters on (the
 * portal's SSE subscription is scoped to one conversation) and is not part of
 * the artifact identity.
 *
 * @param conversationId conversation the artifact belongs to (bus routing key)
 * @param event          {@code "put"} (created/overwritten) or {@code "delete"}
 * @param key            workspace-relative key (scope prefix stripped, e.g.
 *                       {@code reports/lead-export.csv})
 * @param contentType    MIME type of the blob ({@code null} on a delete)
 * @param size           blob size in bytes ({@code 0} on a delete)
 * @param signedUrl      HMAC-signed download URL ({@code null} on a delete)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurWorkspaceEvent(
        String conversationId,
        String event,
        String key,
        String contentType,
        long size,
        String signedUrl) {

    /** Wire token for a create/overwrite event. */
    public static final String EVENT_PUT = "put";
    /** Wire token for a delete event. */
    public static final String EVENT_DELETE = "delete";

    /** A {@code put} event carrying the artifact's current metadata. */
    public static TurWorkspaceEvent put(String conversationId, String key, String contentType,
            long size, String signedUrl) {
        return new TurWorkspaceEvent(conversationId, EVENT_PUT, key, contentType, size, signedUrl);
    }

    /** A {@code delete} event — only the key is meaningful. */
    public static TurWorkspaceEvent delete(String conversationId, String key) {
        return new TurWorkspaceEvent(conversationId, EVENT_DELETE, key, null, 0L, null);
    }
}
