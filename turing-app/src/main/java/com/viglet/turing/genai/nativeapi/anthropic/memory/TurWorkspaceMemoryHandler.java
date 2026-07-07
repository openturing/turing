/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.WorkspaceEntry;

import lombok.extern.slf4j.Slf4j;

/**
 * T163 / §X.9.a — executes the Anthropic {@code memory_20250818} tool commands
 * against a {@link TurAgentWorkspace}, giving Claude a durable file scratchpad
 * ("what this agent remembers") that survives across turns.
 *
 * <p>Anthropic's memory tool addresses files under a fixed {@code /memories}
 * directory and carries six commands: {@code view} / {@code create} /
 * {@code str_replace} / {@code insert} / {@code delete} / {@code rename}. Each
 * model-supplied {@code /memories/...} path is mapped to a workspace key under
 * the {@link #SCOPE_PREFIX memory/} subpath (so it coexists with — and never
 * collides with — the {@code text_editor} authoring files written elsewhere in
 * the same workspace). The store itself is the per-conversation workspace by
 * default; T166 will let an agent point the same handler at a per-user scope.
 *
 * <p>Like {@code TurWorkspaceTextEditor} this class is deliberately free of any
 * Anthropic SDK type — {@link TurAnthropicMemoryService} hands it the decoded
 * input map — so the command semantics are unit-testable against an in-memory
 * workspace.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurWorkspaceMemoryHandler {

    /** The fixed tool name Anthropic uses for {@code memory_20250818}. */
    public static final String TOOL_NAME = "memory";

    /** Workspace subpath every memory file lives under (the roadmap's {@code memory/}). */
    public static final String SCOPE_PREFIX = "memory/";

    /** The model-facing root directory Anthropic's memory tool addresses files under. */
    private static final String MEMORIES_ROOT = "memories";

    private final TurAgentWorkspace workspace;

    public TurWorkspaceMemoryHandler(TurAgentWorkspace workspace) {
        this.workspace = workspace;
    }

    /** Outcome of one memory command: the {@code tool_result} text + error flag. */
    public record MemoryResult(String output, boolean error) {

        static MemoryResult ok(String output) {
            return new MemoryResult(output, false);
        }

        static MemoryResult error(String output) {
            return new MemoryResult(output, true);
        }
    }

    /** Execute one decoded memory command. */
    public MemoryResult execute(String agentId, String conversationId, java.util.Map<?, ?> input) {
        String command = str(input.get("command"));
        if (command == null) {
            return MemoryResult.error("Missing 'command'.");
        }
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "view" -> view(agentId, conversationId, input);
            case "create" -> create(agentId, conversationId, input);
            case "str_replace" -> strReplace(agentId, conversationId, input);
            case "insert" -> insert(agentId, conversationId, input);
            case "delete" -> delete(agentId, conversationId, input);
            case "rename" -> rename(agentId, conversationId, input);
            default -> MemoryResult.error("Unsupported command '" + command + "'.");
        };
    }

    private MemoryResult view(String agentId, String conversationId, java.util.Map<?, ?> input) {
        String path = str(input.get("path"));
        if (path == null) {
            return MemoryResult.error("Missing 'path'.");
        }
        ResolvedPath resolved;
        try {
            resolved = resolve(path);
        } catch (IllegalArgumentException e) {
            return MemoryResult.error("Invalid path '" + path + "': " + e.getMessage());
        }
        if (resolved.isDirectory()) {
            return listDirectory(agentId, conversationId, resolved);
        }
        Optional<String> content = readText(agentId, conversationId, resolved.key());
        if (content.isEmpty()) {
            // A path with no file may still be a directory prefix — fall back to a
            // listing only when there really are nested entries, otherwise the
            // path is genuinely missing.
            if (!childEntries(agentId, conversationId, resolved).isEmpty()) {
                return listDirectory(agentId, conversationId, resolved);
            }
            return MemoryResult.error("File not found: " + path);
        }
        List<String> lines = splitLines(content.get());
        int[] range = viewRange(input.get("view_range"), lines.size());
        StringBuilder sb = new StringBuilder();
        for (int i = range[0]; i <= range[1] && i < lines.size(); i++) {
            sb.append(i + 1).append('\t').append(lines.get(i)).append('\n');
        }
        return MemoryResult.ok(sb.toString());
    }

    private MemoryResult listDirectory(String agentId, String conversationId, ResolvedPath resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append("Directory: ").append(modelPath(resolved.relative())).append('\n');
        for (String relative : childEntries(agentId, conversationId, resolved)) {
            sb.append("- ").append(modelPath(relative)).append('\n');
        }
        return MemoryResult.ok(sb.toString());
    }

    /** Workspace-relative keys (under {@link #SCOPE_PREFIX}) nested below this path. */
    private List<String> childEntries(String agentId, String conversationId, ResolvedPath resolved) {
        List<WorkspaceEntry> entries = workspace.list(agentId, conversationId, resolved.key());
        List<String> relatives = new ArrayList<>();
        for (WorkspaceEntry entry : entries) {
            String key = entry.key();
            if (key != null && key.startsWith(SCOPE_PREFIX)) {
                relatives.add(key.substring(SCOPE_PREFIX.length()));
            }
        }
        return relatives;
    }

    private MemoryResult create(String agentId, String conversationId, java.util.Map<?, ?> input) {
        ResolvedPath resolved;
        try {
            resolved = resolveFile(str(input.get("path")));
        } catch (IllegalArgumentException e) {
            return MemoryResult.error(e.getMessage());
        }
        String fileText = str(input.get("file_text"));
        writeText(agentId, conversationId, resolved.key(), fileText == null ? "" : fileText);
        return MemoryResult.ok("File created: " + modelPath(resolved.relative()));
    }

    private MemoryResult strReplace(String agentId, String conversationId, java.util.Map<?, ?> input) {
        ResolvedPath resolved;
        try {
            resolved = resolveFile(str(input.get("path")));
        } catch (IllegalArgumentException e) {
            return MemoryResult.error(e.getMessage());
        }
        Optional<String> existing = readText(agentId, conversationId, resolved.key());
        if (existing.isEmpty()) {
            return MemoryResult.error("File not found: " + modelPath(resolved.relative()));
        }
        String oldStr = str(input.get("old_str"));
        if (oldStr == null || oldStr.isEmpty()) {
            return MemoryResult.error("Missing 'old_str'.");
        }
        String newStr = str(input.get("new_str"));
        if (newStr == null) {
            newStr = "";
        }
        String content = existing.get();
        int occurrences = countOccurrences(content, oldStr);
        if (occurrences == 0) {
            return MemoryResult.error("No match for 'old_str' in " + modelPath(resolved.relative()) + ".");
        }
        if (occurrences > 1) {
            return MemoryResult.error("Found " + occurrences + " matches for 'old_str' in "
                    + modelPath(resolved.relative()) + "; it must be unique. Add more context.");
        }
        writeText(agentId, conversationId, resolved.key(), content.replace(oldStr, newStr));
        return MemoryResult.ok("Edited " + modelPath(resolved.relative()) + ".");
    }

    private MemoryResult insert(String agentId, String conversationId, java.util.Map<?, ?> input) {
        ResolvedPath resolved;
        try {
            resolved = resolveFile(str(input.get("path")));
        } catch (IllegalArgumentException e) {
            return MemoryResult.error(e.getMessage());
        }
        Optional<String> existing = readText(agentId, conversationId, resolved.key());
        if (existing.isEmpty()) {
            return MemoryResult.error("File not found: " + modelPath(resolved.relative()));
        }
        Integer insertLine = intOrNull(input.get("insert_line"));
        if (insertLine == null) {
            return MemoryResult.error("Missing 'insert_line'.");
        }
        // The memory tool uses "insert_text"; tolerate the editor's "new_str" alias.
        String text = str(input.get("insert_text"));
        if (text == null) {
            text = str(input.get("new_str"));
        }
        if (text == null) {
            text = "";
        }
        List<String> lines = splitLines(existing.get());
        if (insertLine < 0 || insertLine > lines.size()) {
            return MemoryResult.error("insert_line " + insertLine + " is out of range (0.."
                    + lines.size() + ") for " + modelPath(resolved.relative()) + ".");
        }
        lines.add(insertLine, text);
        writeText(agentId, conversationId, resolved.key(), String.join("\n", lines));
        return MemoryResult.ok("Inserted text after line " + insertLine + " in "
                + modelPath(resolved.relative()) + ".");
    }

    private MemoryResult delete(String agentId, String conversationId, java.util.Map<?, ?> input) {
        ResolvedPath resolved;
        try {
            resolved = resolve(str(input.get("path")));
        } catch (IllegalArgumentException e) {
            return MemoryResult.error(e.getMessage());
        }
        if (resolved.isDirectory()) {
            List<WorkspaceEntry> entries = workspace.list(agentId, conversationId, resolved.key());
            int removed = 0;
            for (WorkspaceEntry entry : entries) {
                if (entry.key() != null && entry.key().startsWith(SCOPE_PREFIX)) {
                    workspace.delete(agentId, conversationId, entry.key());
                    removed++;
                }
            }
            return MemoryResult.ok("Deleted " + removed + " file(s) under "
                    + modelPath(resolved.relative()) + ".");
        }
        if (readText(agentId, conversationId, resolved.key()).isEmpty()) {
            return MemoryResult.error("File not found: " + modelPath(resolved.relative()));
        }
        workspace.delete(agentId, conversationId, resolved.key());
        return MemoryResult.ok("Deleted " + modelPath(resolved.relative()) + ".");
    }

    private MemoryResult rename(String agentId, String conversationId, java.util.Map<?, ?> input) {
        // Anthropic uses old_path/new_path; tolerate path/new_path.
        String oldRaw = str(input.get("old_path"));
        if (oldRaw == null) {
            oldRaw = str(input.get("path"));
        }
        ResolvedPath from;
        ResolvedPath to;
        try {
            from = resolveFile(oldRaw);
            to = resolveFile(str(input.get("new_path")));
        } catch (IllegalArgumentException e) {
            return MemoryResult.error(e.getMessage());
        }
        Optional<String> content = readText(agentId, conversationId, from.key());
        if (content.isEmpty()) {
            return MemoryResult.error("File not found: " + modelPath(from.relative()));
        }
        writeText(agentId, conversationId, to.key(), content.get());
        workspace.delete(agentId, conversationId, from.key());
        return MemoryResult.ok("Renamed " + modelPath(from.relative()) + " to "
                + modelPath(to.relative()) + ".");
    }

    private Optional<String> readText(String agentId, String conversationId, String key) {
        return workspace.get(agentId, conversationId, key)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private void writeText(String agentId, String conversationId, String key, String text) {
        workspace.put(agentId, conversationId, key,
                text.getBytes(StandardCharsets.UTF_8), contentType(key));
    }

    /** A resolved path: the relative-to-{@code /memories} portion + the workspace key. */
    record ResolvedPath(String relative, String key, boolean isDirectory) {
    }

    /** Resolve a path that must point at a file (a trailing-slash/dir path is rejected). */
    static ResolvedPath resolveFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Missing 'path'.");
        }
        ResolvedPath resolved = resolve(path);
        if (resolved.isDirectory()) {
            throw new IllegalArgumentException("Expected a file path, got a directory: " + path);
        }
        return resolved;
    }

    /**
     * Map a model-supplied memory path (e.g. {@code /memories/notes.md} or
     * {@code /memories}) to a relative key + workspace key under
     * {@link #SCOPE_PREFIX}. Leading {@code /memories} is optional (a bare
     * relative path is also accepted); {@code ..} traversal is rejected.
     */
    static ResolvedPath resolve(String path) {
        String trimmed = path == null ? "" : path.trim();
        while (trimmed.startsWith("./")) {
            trimmed = trimmed.substring(2);
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        // Strip the optional /memories root the model addresses files under.
        if (trimmed.equals(MEMORIES_ROOT)) {
            trimmed = "";
        } else if (trimmed.startsWith(MEMORIES_ROOT + "/")) {
            trimmed = trimmed.substring(MEMORIES_ROOT.length() + 1);
        }
        boolean directory = trimmed.isEmpty() || trimmed.endsWith("/");
        // Normalize a trailing slash off a directory path for key building.
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("path traversal not allowed");
        }
        if (trimmed.contains("|")) {
            throw new IllegalArgumentException("'|' not allowed in path");
        }
        return new ResolvedPath(trimmed, SCOPE_PREFIX + trimmed, directory);
    }

    /** Present a relative key back to the model rooted at {@code /memories}. */
    private static String modelPath(String relative) {
        return relative.isEmpty() ? "/" + MEMORIES_ROOT : "/" + MEMORIES_ROOT + "/" + relative;
    }

    private static String contentType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "text/plain";
    }

    /** Split on newlines preserving empty trailing segments; never returns null. */
    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content.isEmpty()) {
            return lines;
        }
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines.add(content.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(content.substring(start));
        return lines;
    }

    /** Resolve a 1-based inclusive {@code [start, end]} view range to 0-based bounds. */
    private static int[] viewRange(Object rangeValue, int lineCount) {
        int lastIndex = Math.max(0, lineCount - 1);
        if (rangeValue instanceof List<?> list && list.size() >= 2) {
            Integer start = intOrNull(list.get(0));
            Integer end = intOrNull(list.get(1));
            int from = start == null ? 1 : Math.max(1, start);
            int to = (end == null || end < 0) ? lineCount : Math.min(lineCount, end);
            return new int[] { Math.min(from - 1, lastIndex), Math.min(Math.max(to - 1, from - 1), lastIndex) };
        }
        return new int[] { 0, lastIndex };
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
