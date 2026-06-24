/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.mcp;

/**
 * Wire transport an HTTP MCP server speaks (T294). Only meaningful when the
 * server's {@link TurMcpServerConnectionType} is {@code HTTP}; ignored for
 * {@code COMMAND} (stdio) servers.
 *
 * <ul>
 *   <li>{@link #SSE} — the legacy two-endpoint HTTP+SSE protocol: a
 *       {@code GET /sse} opens the event stream and an {@code endpoint} event
 *       tells the client where to POST messages. This is the default so
 *       existing servers (and rows with a {@code null} column) keep working
 *       unchanged.</li>
 *   <li>{@link #STREAMABLE_HTTP} — the newer single-endpoint Streamable HTTP
 *       protocol (default path {@code /mcp}). A different wire protocol with
 *       no fallback to the legacy SSE handshake, so it can't silently replace
 *       SSE — each server opts in explicitly.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurMcpServerTransportType {
    SSE,
    STREAMABLE_HTTP
}
