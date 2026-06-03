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

import java.util.List;
import java.util.Optional;

/**
 * Per-conversation key-value-blob store for AI agents (T111).
 *
 * <p>A thin façade over the pluggable {@link com.viglet.turing.service.storage.TurStorageService}
 * (NoOp / Filesystem / MinIO, selected by {@code turing.storage.type}). Each
 * conversation gets an isolated namespace; the backend writes under the
 * T78 multi-tenant path
 * {@code tenants/{agentId}/{conversationId}/workspace/<key>}.
 *
 * <h2>Workspace vs slots</h2>
 * Slots are flat scalars/JSON published on the slot bus. The workspace holds
 * <b>byte-blobs</b> with a content type and an HMAC-signed serve URL — PDFs,
 * images, CSVs, intermediate tool artifacts. The LLM sees workspace contents
 * as URL references, never inlined into the prompt.
 *
 * <h2>Keys</h2>
 * A {@code key} is workspace-relative and may contain {@code /} to express
 * folders (e.g. {@code reports/lead-export.csv}). Path-traversal sequences
 * ({@code ..}, absolute paths, {@code |}) are rejected so a key can never
 * escape its conversation scope.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurAgentWorkspace {

    /**
     * Stores (or overwrites) the blob at {@code key} for the given
     * agent + conversation scope.
     *
     * @param agentId        owning agent id (path-isolated).
     * @param conversationId owning conversation id (path-isolated).
     * @param key            workspace-relative key.
     * @param content        the bytes to store.
     * @param contentType    MIME type (null → guessed from the key).
     */
    void put(String agentId, String conversationId, String key, byte[] content, String contentType);

    /**
     * Reads the blob at {@code key}, or {@link Optional#empty()} when it
     * does not exist (or storage is disabled).
     */
    Optional<byte[]> get(String agentId, String conversationId, String key);

    /**
     * Lists every artifact whose key starts with {@code prefix} (recursive,
     * scoped to this conversation's workspace). A null/blank prefix lists
     * the whole workspace. Returns an empty list when storage is disabled.
     */
    List<WorkspaceEntry> list(String agentId, String conversationId, String prefix);

    /** Deletes the blob at {@code key}. No-op when it does not exist. */
    void delete(String agentId, String conversationId, String key);

    /**
     * Builds an HMAC-signed, time-limited relative URL that serves the blob
     * at {@code key} through {@code /api/v2/workspace/file}. The signature
     * binds the agent + conversation + key + expiry, so a leaked URL cannot
     * be replayed for a different key/conversation or beyond its TTL
     * (mirrors the T79 code-interpreter URL-signing pattern).
     */
    String signedUrl(String agentId, String conversationId, String key);
}
