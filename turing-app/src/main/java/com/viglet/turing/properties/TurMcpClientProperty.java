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

    /**
     * T1037 / §CVI.2 — how long the client waits for an MCP server to answer.
     *
     * <p>Was a hardcoded 30 seconds. Tool discovery runs on the <em>chat path</em>
     * — an agent's tools are resolved per turn — so an unreachable server made
     * every turn of every conversation on every agent that had it attached wait
     * out that timeout, which a user experiences as a chat that stopped
     * responding. Ten seconds is generous for a {@code tools/list} against a
     * healthy server and short enough that a sick one degrades instead of hangs.
     *
     * <p>Raise it for a slow-but-alive server behind a distant hop; lowering it
     * further is safe on a local one.
     *
     * @since 2026.3.4
     */
    private java.time.Duration requestTimeout = java.time.Duration.ofSeconds(10);

    /**
     * T1037 / §CVI.2 — how long a server that just failed to connect is skipped
     * before being tried again.
     *
     * <p>A failed client is dropped from the cache (it must be — a half-built one
     * cannot be served to the next caller), which without this meant the next
     * turn paid the whole timeout again. The agent loses that server's tools
     * either way; paying the timeout on every turn buys nothing.
     *
     * <p>An admin edit to the server's URL, transport or credential clears the
     * skip immediately, so fixing a rotated token does not mean waiting this out.
     * {@link java.time.Duration#ZERO} restores the legacy retry-every-turn
     * behaviour.
     *
     * @since 2026.3.4
     */
    private java.time.Duration failureBackoff = java.time.Duration.ofSeconds(60);
}
