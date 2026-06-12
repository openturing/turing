/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import lombok.extern.slf4j.Slf4j;

/**
 * T246 / §XIII.6 — wraps an MCP tool callback so {@link TurMcpToolScopePolicy}
 * vetoes execution the caller is not authorized for, <em>inside</em> the call
 * (defense in depth, independent of what the endpoint listed).
 *
 * <p>The current {@link Authentication} is read from the {@link SecurityContextHolder}:
 * MCP requests are processed synchronously on a servlet thread, so the
 * resource-server's {@code JwtAuthenticationToken} is in scope while the tool
 * runs. On the loopback/no-auth path there is no authentication and the policy
 * decides accordingly.
 *
 * <p>A denied call returns a plain refusal string rather than throwing, so the
 * MCP client receives a clean tool error instead of a transport failure
 * (errors-as-text — the established convention for these callbacks).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurMcpScopeToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final TurMcpToolScopePolicy scopePolicy;

    public TurMcpScopeToolCallback(ToolCallback delegate, TurMcpToolScopePolicy scopePolicy) {
        this.delegate = delegate;
        this.scopePolicy = scopePolicy;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return guard() ? delegate.call(toolInput) : denied();
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return guard() ? delegate.call(toolInput, toolContext) : denied();
    }

    private boolean guard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return scopePolicy.isAllowed(toolName(), authentication);
    }

    private String denied() {
        log.warn("[MCP] Denied tool '{}' — caller lacks the required scope", toolName());
        return "Error: not authorized to call '" + toolName()
                + "'. This MCP tool requires additional scope on your access token.";
    }

    private String toolName() {
        return delegate.getToolDefinition().name();
    }

    /** Wrap every callback with the scope guard (outermost decorator). */
    public static ToolCallback[] wrap(ToolCallback[] callbacks, TurMcpToolScopePolicy scopePolicy) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new TurMcpScopeToolCallback(callbacks[i], scopePolicy);
        }
        return wrapped;
    }
}
