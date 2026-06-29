/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.anthropic.memory.TurWorkspaceMemoryHandler.MemoryResult;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.WorkspaceEntry;

class TurWorkspaceMemoryHandlerTest {

    /** In-memory workspace whose {@code list} honours the recursive prefix. */
    private static final class InMemoryWorkspace implements TurAgentWorkspace {
        private final TreeMap<String, byte[]> store = new TreeMap<>();

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
            List<WorkspaceEntry> out = new ArrayList<>();
            String p = prefix == null ? "" : prefix;
            for (Map.Entry<String, byte[]> e : store.entrySet()) {
                if (e.getKey().startsWith(p)) {
                    out.add(new WorkspaceEntry(e.getKey(), e.getValue().length, "text/plain", null, null));
                }
            }
            return out;
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

        boolean has(String key) {
            return store.containsKey(key);
        }
    }

    private InMemoryWorkspace workspace;
    private TurWorkspaceMemoryHandler handler;

    @BeforeEach
    void setUp() {
        workspace = new InMemoryWorkspace();
        handler = new TurWorkspaceMemoryHandler(workspace);
    }

    private MemoryResult run(Map<String, Object> input) {
        return handler.execute("agent", "conv", input);
    }

    @Test
    void createMapsMemoriesPathToMemorySubpath() {
        MemoryResult created = run(Map.of("command", "create", "path", "/memories/prefs.md",
                "file_text", "likes terse answers"));
        assertThat(created.error()).isFalse();
        // /memories/prefs.md → workspace key memory/prefs.md
        assertThat(workspace.text("memory/prefs.md")).isEqualTo("likes terse answers");
    }

    @Test
    void createThenViewReturnsNumberedLines() {
        run(Map.of("command", "create", "path", "/memories/notes.md", "file_text", "line one\nline two"));
        MemoryResult viewed = run(Map.of("command", "view", "path", "/memories/notes.md"));
        assertThat(viewed.error()).isFalse();
        assertThat(viewed.output()).isEqualTo("1\tline one\n2\tline two\n");
    }

    @Test
    void viewOnMemoriesRootListsFiles() {
        run(Map.of("command", "create", "path", "/memories/a.md", "file_text", "a"));
        run(Map.of("command", "create", "path", "/memories/sub/b.md", "file_text", "b"));
        MemoryResult listed = run(Map.of("command", "view", "path", "/memories"));
        assertThat(listed.error()).isFalse();
        assertThat(listed.output()).contains("/memories/a.md").contains("/memories/sub/b.md");
    }

    @Test
    void strReplaceEditsWhenUniqueAndFailsOtherwise() {
        run(Map.of("command", "create", "path", "/memories/doc.md", "file_text", "alpha beta gamma"));

        MemoryResult ok = run(Map.of("command", "str_replace", "path", "/memories/doc.md",
                "old_str", "beta", "new_str", "BETA"));
        assertThat(ok.error()).isFalse();
        assertThat(workspace.text("memory/doc.md")).isEqualTo("alpha BETA gamma");

        MemoryResult noMatch = run(Map.of("command", "str_replace", "path", "/memories/doc.md",
                "old_str", "zeta", "new_str", "x"));
        assertThat(noMatch.error()).isTrue();
        assertThat(noMatch.output()).contains("No match");
    }

    @Test
    void insertUsesInsertTextAlias() {
        run(Map.of("command", "create", "path", "/memories/doc.md", "file_text", "a\nc"));
        MemoryResult inserted = run(Map.of("command", "insert", "path", "/memories/doc.md",
                "insert_line", 1, "insert_text", "b"));
        assertThat(inserted.error()).isFalse();
        assertThat(workspace.text("memory/doc.md")).isEqualTo("a\nb\nc");
    }

    @Test
    void deleteRemovesFile() {
        run(Map.of("command", "create", "path", "/memories/gone.md", "file_text", "x"));
        assertThat(workspace.has("memory/gone.md")).isTrue();

        MemoryResult deleted = run(Map.of("command", "delete", "path", "/memories/gone.md"));
        assertThat(deleted.error()).isFalse();
        assertThat(workspace.has("memory/gone.md")).isFalse();

        MemoryResult again = run(Map.of("command", "delete", "path", "/memories/gone.md"));
        assertThat(again.error()).isTrue();
        assertThat(again.output()).contains("File not found");
    }

    @Test
    void renameMovesContentToNewPath() {
        run(Map.of("command", "create", "path", "/memories/old.md", "file_text", "keep me"));
        MemoryResult renamed = run(Map.of("command", "rename",
                "old_path", "/memories/old.md", "new_path", "/memories/new.md"));
        assertThat(renamed.error()).isFalse();
        assertThat(workspace.has("memory/old.md")).isFalse();
        assertThat(workspace.text("memory/new.md")).isEqualTo("keep me");
    }

    @Test
    void viewMissingFileIsAnError() {
        MemoryResult viewed = run(Map.of("command", "view", "path", "/memories/ghost.md"));
        assertThat(viewed.error()).isTrue();
        assertThat(viewed.output()).contains("File not found");
    }

    @Test
    void pathTraversalIsRejected() {
        MemoryResult result = run(Map.of("command", "create", "path", "/memories/../escape.md",
                "file_text", "x"));
        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("traversal");
    }

    @Test
    void unsupportedCommandIsAnError() {
        MemoryResult result = run(Map.of("command", "frobnicate", "path", "/memories/x.md"));
        assertThat(result.error()).isTrue();
        assertThat(result.output()).contains("Unsupported command");
    }

    @Test
    void resolveStripsMemoriesRootAndMapsToMemoryPrefix() {
        assertThat(TurWorkspaceMemoryHandler.resolve("/memories/notes.md").key())
                .isEqualTo("memory/notes.md");
        // A bare relative path is tolerated and still lands under memory/.
        assertThat(TurWorkspaceMemoryHandler.resolve("notes.md").key()).isEqualTo("memory/notes.md");
        assertThat(TurWorkspaceMemoryHandler.resolve("/memories").isDirectory()).isTrue();
    }
}
