/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T650 / §XXXVII.12 — guardrails for the agent-as-MCP-<b>client</b> path
 * ({@code turing.mcp-client.*}). An admin-configured MCP server can point the
 * client at an arbitrary URL (SSRF) or, for the stdio transport, run an
 * arbitrary local {@code command} (RCE-by-config). These knobs let a deployment
 * lock that down. Both default to the legacy (permissive) behaviour so an
 * existing MCP-client setup is unaffected until the operator opts in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.mcp-client")
public class TurMcpClientProperty {

    /**
     * When {@code true}, an HTTP MCP-client URL that resolves to a private /
     * loopback / link-local address is refused (via {@link com.viglet.turing.spring.security.ssrf.TurSsrfGuard}).
     * Default {@code false} because a legitimate MCP server often lives on an
     * internal network — turn on for an internet-only egress posture.
     */
    private boolean blockPrivateUrls = false;

    /**
     * Allowlist of permitted stdio {@code command} executables (matched on the
     * command's base name, case-insensitively — e.g. {@code npx}, {@code uvx},
     * {@code python}). When EMPTY (default) any command is allowed (legacy). When
     * non-empty, a stdio MCP server whose command is not listed is refused,
     * blocking arbitrary local-process execution by configuration.
     */
    private List<String> allowedStdioCommands = new ArrayList<>();
}
