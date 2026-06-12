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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurMcpScopeToolCallback} — verifies it consults
 * {@link TurMcpToolScopePolicy} before delegating, and refuses (without
 * delegating) when the policy denies the call.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpScopeToolCallbackTest {

    @Mock
    private ToolCallback delegate;

    @Mock
    private ToolDefinition toolDefinition;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private TurMcpToolScopePolicy policy(boolean requireAuth) {
        TurConfigProperties p = new TurConfigProperties();
        p.getMcpServer().setRequireAuth(requireAuth);
        return new TurMcpToolScopePolicy(p);
    }

    @Test
    void allowedCall_delegates() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("dsl_search");
        when(delegate.call(anyString())).thenReturn("ok");

        // require-auth=false → read tool allowed without a principal.
        TurMcpScopeToolCallback callback = new TurMcpScopeToolCallback(delegate, policy(false));

        String result = callback.call("{}");

        assertEquals("ok", result);
        verify(delegate).call("{}");
    }

    @Test
    void deniedCall_returnsRefusalAndDoesNotDelegate() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("dsl_search");

        // require-auth=true with no authentication → denied.
        SecurityContextHolder.clearContext();
        TurMcpScopeToolCallback callback = new TurMcpScopeToolCallback(delegate, policy(true));

        String result = callback.call("{}");

        assertTrue(result.startsWith("Error: not authorized"), () -> "unexpected: " + result);
        verify(delegate, never()).call(anyString());
    }

    @Test
    void allowedWhenAuthenticated_delegates() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("dsl_search");
        when(delegate.call(anyString())).thenReturn("ok");

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", "n/a", "ROLE_USER"));
        TurMcpScopeToolCallback callback = new TurMcpScopeToolCallback(delegate, policy(true));

        String result = callback.call("{}");

        assertEquals("ok", result);
        verify(delegate).call("{}");
    }
}
