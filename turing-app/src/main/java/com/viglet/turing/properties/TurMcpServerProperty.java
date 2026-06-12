/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * T245 / §XIII — configuration for the Turing-as-an-MCP-server surface, bound
 * under {@code turing.mcp-server.*}.
 *
 * <p>The Spring AI MCP server itself is toggled by the standard
 * {@code spring.ai.mcp.server.enabled} property (default {@code false} in
 * {@code application.yaml}); when it is off there is no {@code /mcp} endpoint
 * and a single-tenant install is byte-for-byte unchanged. This holder carries
 * the Turing-specific governance on top of that endpoint.
 *
 * <p>{@link #loopbackOnly} is the T245 trust boundary: until the OAuth 2.1
 * resource-server gate (T246) lands, the {@code /mcp} endpoint must only answer
 * requests originating from the loopback interface. It defaults to {@code true}
 * so that merely enabling the MCP server can never accidentally expose the tool
 * catalog to a remote, unauthenticated client.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
public class TurMcpServerProperty {

    /**
     * When {@code true} (the default), the {@code /mcp} endpoint rejects any
     * request whose remote address is not the loopback interface with HTTP 403.
     * This is the T245 loopback-first trust boundary; it stays on until a remote
     * deployment opts in <em>after</em> wiring the T246 OAuth 2.1 resource-server
     * authentication.
     */
    private boolean loopbackOnly = true;

    /**
     * T246 — when {@code true}, {@code /mcp} is protected by an OAuth 2.1
     * resource-server gate: every request must carry a valid Keycloak-issued JWT
     * bearer token (validated against the configured
     * {@code spring.security.oauth2.resourceserver.jwt.*} decoder), and tool
     * execution is scoped to the token's granted authorities. Default
     * {@code false} keeps the T245 loopback-only posture for local development.
     * A remote deployment is expected to set this {@code true} together with
     * {@link #loopbackOnly}{@code =false}. Fails closed: if {@code true} but no
     * {@code JwtDecoder} is configured, {@code /mcp} denies every request.
     */
    private boolean requireAuth = false;

    /**
     * T246 — the granted authority a token must hold to invoke a <em>write</em>
     * MCP tool (the gated ingestion tools land in T253). Read tools never require
     * it. Spring maps an OAuth2 {@code scope}/{@code scp} claim value {@code x}
     * to the authority {@code SCOPE_x}, so the default matches a token granted
     * the {@code mcp:write} scope.
     */
    private String writeScope = "SCOPE_mcp:write";

    /**
     * T253 — master switch for the gated write/ingestion MCP tools
     * ({@code index_document}, {@code deindex_document}, {@code reindex_site}).
     * Default {@code false}: the write tools are not even registered in the MCP
     * catalog, so a read-only deployment never exposes them. When {@code true}
     * they appear but still require the {@link #writeScope} authority per call
     * (enforced by {@code TurMcpToolScopePolicy}) — defense in depth: opt-in
     * <em>and</em> scoped.
     */
    private boolean writeEnabled = false;
}
