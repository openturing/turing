/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;

/**
 * Tests for {@link TurToolResultOffloadCallback} — the T114 decorator that
 * offloads oversized tool results to the per-conversation
 * {@link TurAgentWorkspace} and replaces them with a {@code workspace://}
 * reference.
 */
class TurToolResultOffloadCallbackTest {

    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";
    private static final int MAX = 100;

    private static ToolContext activeContext() {
        return new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, AGENT,
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, CONV));
    }

    private static ToolCallback delegateReturning(String result) {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition())
                .thenReturn(new DefaultToolDefinition("search_site", "desc", "{}"));
        when(delegate.call(any(), any(ToolContext.class))).thenReturn(result);
        when(delegate.call(any())).thenReturn(result);
        return delegate;
    }

    @Test
    void offloadsLargeResultAndReturnsReference() {
        String big = "x".repeat(MAX + 50);
        ToolCallback delegate = delegateReturning(big);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        String out = cb.call("{}", activeContext());

        assertThat(out).startsWith("Stored at workspace://" + TurToolResultOffloadCallback.OFFLOAD_PREFIX
                + "search_site-");
        assertThat(out).contains("Call workspace_read with key=");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(workspace).put(eq(AGENT), eq(CONV), key.capture(), bytes.capture(), eq("application/json"));
        assertThat(key.getValue()).startsWith(TurToolResultOffloadCallback.OFFLOAD_PREFIX + "search_site-")
                .endsWith(".json");
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo(big);
        // The reference embeds the exact key that was written.
        assertThat(out).contains(key.getValue());
    }

    @Test
    void leavesSmallResultInline() {
        String small = "y".repeat(MAX);
        ToolCallback delegate = delegateReturning(small);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        assertThat(cb.call("{}", activeContext())).isEqualTo(small);
        verifyNoInteractions(workspace);
    }

    @Test
    void passesThroughWhenNoConversationContext() {
        String big = "z".repeat(MAX + 50);
        ToolCallback delegate = delegateReturning(big);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        // empty context → no agent/conv → no offload
        assertThat(cb.call("{}", new ToolContext(Map.of()))).isEqualTo(big);
        verifyNoInteractions(workspace);
    }

    @Test
    void oneArgPathNeverOffloads() {
        String big = "w".repeat(MAX + 50);
        ToolCallback delegate = delegateReturning(big);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        assertThat(cb.call("{}")).isEqualTo(big);
        verifyNoInteractions(workspace);
    }

    @Test
    void fallsBackToInlineWhenWorkspaceWriteFails() {
        String big = "q".repeat(MAX + 50);
        ToolCallback delegate = delegateReturning(big);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        doThrow(new IllegalStateException("storage down"))
                .when(workspace).put(any(), any(), any(), any(), any());
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        assertThat(cb.call("{}", activeContext())).isEqualTo(big);
    }

    @Test
    void nullResultIsPassedThrough() {
        ToolCallback delegate = delegateReturning(null);
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurToolResultOffloadCallback cb = new TurToolResultOffloadCallback(delegate, workspace, MAX);

        assertThat(cb.call("{}", activeContext())).isNull();
        verifyNoInteractions(workspace);
    }
}
