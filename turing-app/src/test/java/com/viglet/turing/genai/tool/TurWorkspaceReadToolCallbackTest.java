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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;

/**
 * Tests for {@link TurWorkspaceReadToolCallback} — the always-present
 * {@code workspace_read} platform tool that resolves a {@code workspace://}
 * reference offloaded by {@link TurToolResultOffloadCallback} (T114).
 */
class TurWorkspaceReadToolCallbackTest {

    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";
    private static final String KEY = "tool-results/search_site-123-1.json";

    private static ToolContext activeContext() {
        return new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, AGENT,
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, CONV));
    }

    @Test
    void exposesWorkspaceReadDefinition() {
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(mock(TurAgentWorkspace.class));
        assertThat(cb.getToolDefinition().name()).isEqualTo(TurWorkspaceReadToolCallback.TOOL_NAME);
        assertThat(cb.getToolDefinition().inputSchema()).contains("\"key\"");
    }

    @Test
    void readsArtifactByKey() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        String content = "{\"results\":[1,2,3]}";
        when(workspace.get(AGENT, CONV, KEY))
                .thenReturn(Optional.of(content.getBytes(StandardCharsets.UTF_8)));
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(workspace);

        String out = cb.call("{\"key\":\"" + KEY + "\"}", activeContext());

        assertThat(out).isEqualTo(content);
    }

    @Test
    void acceptsBareStringKey() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        when(workspace.get(AGENT, CONV, KEY))
                .thenReturn(Optional.of("ok".getBytes(StandardCharsets.UTF_8)));
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(workspace);

        assertThat(cb.call(KEY, activeContext())).isEqualTo("ok");
    }

    @Test
    void returnsNotFoundWhenAbsent() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        when(workspace.get(AGENT, CONV, KEY)).thenReturn(Optional.empty());
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(workspace);

        assertThat(cb.call("{\"key\":\"" + KEY + "\"}", activeContext()))
                .contains("No workspace artifact found");
    }

    @Test
    void unavailableWithoutConversationContext() {
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(mock(TurAgentWorkspace.class));

        assertThat(cb.call("{\"key\":\"" + KEY + "\"}", new ToolContext(Map.of())))
                .contains("no active conversation context");
        assertThat(cb.call("{\"key\":\"" + KEY + "\"}")).contains("no active conversation context");
    }

    @Test
    void requiresKeyArgument() {
        TurWorkspaceReadToolCallback cb = new TurWorkspaceReadToolCallback(mock(TurAgentWorkspace.class));

        assertThat(cb.call("{}", activeContext())).contains("requires a non-blank 'key'");
    }
}
