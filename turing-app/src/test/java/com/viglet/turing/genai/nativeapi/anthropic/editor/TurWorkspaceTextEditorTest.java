/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.anthropic.editor.TurWorkspaceTextEditor.EditResult;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.WorkspaceEntry;

class TurWorkspaceTextEditorTest {

    /** Minimal in-memory workspace keyed by {@code key} (agent/conv ignored). */
    private static final class InMemoryWorkspace implements TurAgentWorkspace {
        private final Map<String, byte[]> store = new HashMap<>();

        @Override
        public void put(String agentId, String conversationId, String key, byte[] content,
                String contentType) {
            store.put(key, content);
        }

        @Override
        public Optional<byte[]> get(String agentId, String conversationId, String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public List<WorkspaceEntry> list(String agentId, String conversationId, String prefix) {
            return new ArrayList<>();
        }

        @Override
        public void delete(String agentId, String conversationId, String key) {
            store.remove(key);
        }

        @Override
        public String signedUrl(String agentId, String conversationId, String key) {
            return "/api/v2/workspace/file?key=" + key;
        }

        String text(String key) {
            byte[] bytes = store.get(key);
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private InMemoryWorkspace workspace;
    private TurWorkspaceTextEditor editor;
    private Map<String, String> undo;

    @BeforeEach
    void setUp() {
        workspace = new InMemoryWorkspace();
        editor = new TurWorkspaceTextEditor(workspace);
        undo = new HashMap<>();
    }

    private EditResult run(Map<String, Object> input) {
        return editor.execute("agent", "conv", input, undo);
    }

    @Test
    void createThenViewReturnsNumberedLines() {
        EditResult created = run(Map.of("command", "create", "path", "/playbook.md",
                "file_text", "line one\nline two"));
        assertThat(created.error()).isFalse();
        assertThat(workspace.text("playbook.md")).isEqualTo("line one\nline two");

        EditResult viewed = run(Map.of("command", "view", "path", "playbook.md"));
        assertThat(viewed.error()).isFalse();
        assertThat(viewed.output()).isEqualTo("1\tline one\n2\tline two\n");
    }

    @Test
    void strReplaceEditsWhenUniqueAndFailsOtherwise() {
        run(Map.of("command", "create", "path", "doc.md", "file_text", "alpha beta gamma"));

        EditResult ok = run(Map.of("command", "str_replace", "path", "doc.md",
                "old_str", "beta", "new_str", "BETA"));
        assertThat(ok.error()).isFalse();
        assertThat(workspace.text("doc.md")).isEqualTo("alpha BETA gamma");

        EditResult noMatch = run(Map.of("command", "str_replace", "path", "doc.md",
                "old_str", "zeta", "new_str", "x"));
        assertThat(noMatch.error()).isTrue();
        assertThat(noMatch.output()).contains("No match");
    }

    @Test
    void strReplaceRejectsNonUniqueMatch() {
        run(Map.of("command", "create", "path", "doc.md", "file_text", "x x x"));
        EditResult ambiguous = run(Map.of("command", "str_replace", "path", "doc.md",
                "old_str", "x", "new_str", "y"));
        assertThat(ambiguous.error()).isTrue();
        assertThat(ambiguous.output()).contains("3 matches");
    }

    @Test
    void insertAddsLineAtPosition() {
        run(Map.of("command", "create", "path", "doc.md", "file_text", "a\nc"));
        EditResult inserted = run(Map.of("command", "insert", "path", "doc.md",
                "insert_line", 1, "new_str", "b"));
        assertThat(inserted.error()).isFalse();
        assertThat(workspace.text("doc.md")).isEqualTo("a\nb\nc");
    }

    @Test
    void undoRevertsLastEdit() {
        run(Map.of("command", "create", "path", "doc.md", "file_text", "original"));
        run(Map.of("command", "str_replace", "path", "doc.md", "old_str", "original", "new_str", "changed"));
        assertThat(workspace.text("doc.md")).isEqualTo("changed");

        EditResult undone = run(Map.of("command", "undo_edit", "path", "doc.md"));
        assertThat(undone.error()).isFalse();
        assertThat(workspace.text("doc.md")).isEqualTo("original");

        // undo_edit is single-level: with no further edit recorded, another undo errors.
        EditResult again = run(Map.of("command", "undo_edit", "path", "doc.md"));
        assertThat(again.error()).isTrue();
        assertThat(again.output()).contains("No edit to undo");
    }

    @Test
    void undoOfACreateDeletesTheFile() {
        run(Map.of("command", "create", "path", "fresh.md", "file_text", "brand new"));
        assertThat(workspace.text("fresh.md")).isEqualTo("brand new");

        EditResult undoCreate = run(Map.of("command", "undo_edit", "path", "fresh.md"));
        assertThat(undoCreate.error()).isFalse();
        assertThat(workspace.text("fresh.md")).isNull();
    }

    @Test
    void viewMissingFileIsAnError() {
        EditResult viewed = run(Map.of("command", "view", "path", "ghost.md"));
        assertThat(viewed.error()).isTrue();
        assertThat(viewed.output()).contains("File not found");
    }

    @Test
    void pathTraversalIsRejected() {
        EditResult result = run(Map.of("command", "create", "path", "../escape.md", "file_text", "x"));
        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("Invalid path");
    }

    @Test
    void toKeyStripsLeadingSlashesAndDotSlash() {
        assertThat(TurWorkspaceTextEditor.toKey("/a/b.md")).isEqualTo("a/b.md");
        assertThat(TurWorkspaceTextEditor.toKey("./notes.md")).isEqualTo("notes.md");
    }
}
