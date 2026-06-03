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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.WorkspaceEntry;

/**
 * Tests for {@link TurCustomToolWorkspaceHelper} — the T112 Groovy binding
 * ({@code workspace}) that lets Custom Tools persist and read per-conversation
 * artifacts through {@link TurAgentWorkspace} (T111).
 */
class TurCustomToolWorkspaceHelperTest {

    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";

    @Test
    void putStringShouldEncodeUtf8AndDelegateToScopedWorkspace() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        helper.put("drafts/note.txt", "olá café");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(workspace).put(eq(AGENT), eq(CONV), eq("drafts/note.txt"), bytes.capture(),
                eq("text/plain; charset=utf-8"));
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo("olá café");
    }

    @Test
    void putBytesShouldPassContentTypeThrough() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        byte[] csv = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        helper.put("reports/x.csv", csv, "text/csv");

        verify(workspace).put(AGENT, CONV, "reports/x.csv", csv, "text/csv");
    }

    @Test
    void getShouldReturnBytesWhenPresentAndNullWhenAbsent() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        when(workspace.get(AGENT, CONV, "k1")).thenReturn(Optional.of(payload));
        when(workspace.get(AGENT, CONV, "missing")).thenReturn(Optional.empty());

        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        assertThat(helper.get("k1")).isEqualTo(payload);
        assertThat(helper.get("missing")).isNull();
    }

    @Test
    void getTextShouldDecodeUtf8() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        when(workspace.get(AGENT, CONV, "k"))
                .thenReturn(Optional.of("résumé".getBytes(StandardCharsets.UTF_8)));

        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        assertThat(helper.getText("k")).isEqualTo("résumé");
        assertThat(helper.getText("absent")).isNull();
    }

    @Test
    void listShouldDelegateAndNoArgVariantListsWholeWorkspace() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        WorkspaceEntry entry = new WorkspaceEntry("reports/x.csv", 8L, "text/csv", "2026-06-03", "/api/v2/workspace/file?...");
        when(workspace.list(AGENT, CONV, "reports/")).thenReturn(List.of(entry));
        when(workspace.list(AGENT, CONV, null)).thenReturn(List.of(entry));

        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        assertThat(helper.list("reports/")).containsExactly(entry);
        assertThat(helper.list()).containsExactly(entry);
    }

    @Test
    void urlShouldReturnSignedUrl() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        when(workspace.signedUrl(AGENT, CONV, "reports/x.csv")).thenReturn("/api/v2/workspace/file?sig=abc");

        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        assertThat(helper.url("reports/x.csv")).isEqualTo("/api/v2/workspace/file?sig=abc");
    }

    @Test
    void deleteShouldDelegate() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        helper.delete("drafts/note.txt");

        verify(workspace).delete(AGENT, CONV, "drafts/note.txt");
    }

    @Test
    void noOpModeWhenTenantContextMissing() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace);

        helper.put("k", "v");
        helper.put("k", new byte[] {1}, "application/octet-stream");
        helper.delete("k");

        assertThat(helper.get("k")).isNull();
        assertThat(helper.getText("k")).isNull();
        assertThat(helper.url("k")).isNull();
        assertThat(helper.list("p/")).isEmpty();
        assertThat(helper.list()).isEmpty();
        assertThat(helper.getConversationId()).isNull();

        verifyNoInteractions(workspace);
    }

    @Test
    void nullOrBlankKeyIsNoOpEvenWithTenantContext() {
        TurAgentWorkspace workspace = mock(TurAgentWorkspace.class);
        TurCustomToolWorkspaceHelper helper = new TurCustomToolWorkspaceHelper(workspace, AGENT, CONV);

        helper.put("   ", "v");
        helper.delete(null);
        assertThat(helper.get(" ")).isNull();
        assertThat(helper.url(null)).isNull();

        verify(workspace, never()).put(any(), any(), any(), any(), any());
        verify(workspace, never()).delete(any(), any(), any());
        verify(workspace, never()).get(any(), any(), any());
        verify(workspace, never()).signedUrl(any(), any(), any());
    }
}
