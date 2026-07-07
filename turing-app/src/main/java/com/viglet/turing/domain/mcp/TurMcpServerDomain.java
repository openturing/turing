/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.domain.mcp;

import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerTransportType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerType;

/**
 * Domain entity for an MCP (Model Context Protocol) server registration —
 * the connection details an agent uses to invoke remote tools at runtime.
 * Free of JPA / Jackson annotations and immutable.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurMcpServerDomain(
        String id,
        String title,
        String description,
        String icon,
        String url,
        String command,
        String args,
        TurMcpServerType type,
        TurMcpServerConnectionType connectionType,
        TurMcpServerTransportType transportType,
        int enabled) {

    /** True when the MCP server is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }

    /**
     * T294 — true when this HTTP server should use the Streamable HTTP
     * transport. A {@code null} {@link #transportType} (legacy rows, COMMAND
     * servers) means the legacy SSE transport, so callers default to SSE.
     */
    public boolean isStreamableHttp() {
        return transportType == TurMcpServerTransportType.STREAMABLE_HTTP;
    }
}
