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
 * A single artifact in an agent's per-conversation workspace.
 *
 * <p>Distinct from a chat <em>slot</em>: slots are flat scalars/JSON held in
 * the slot bus; a {@code WorkspaceEntry} describes a byte-blob (PDF, image,
 * CSV, markdown, …) addressable by {@code key} and servable via an
 * HMAC-signed URL.
 *
 * <p>The {@code key} is workspace-relative (e.g. {@code reports/lead-export.csv})
 * — the storage-backend scope prefix
 * ({@code tenants/{agentId}/{conversationId}/workspace/}) is stripped before
 * the entry is handed to callers, so a key never leaks the tenant path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record WorkspaceEntry(
        String key,
        long sizeBytes,
        String contentType,
        String lastModified,
        String signedUrl
) {
}
