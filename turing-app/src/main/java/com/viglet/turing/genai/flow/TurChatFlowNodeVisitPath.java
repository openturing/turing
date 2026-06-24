/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T237 / §VII.10.b — pure helper for the per-turn node-visit log that backs
 * the path-aware funnel. A conversation's path is the ordered list of node
 * ids the cursor settled on across turns, stored as a JSON array string on
 * {@code chat_flow_state} (live accumulator) and snapshotted onto
 * {@code chat_flow_submission} at flow end.
 *
 * <p>Consecutive duplicates are collapsed (re-asking the same {@code aiQuestion}
 * after a rejected answer must not inflate the visit count) and the list is
 * capped at {@link #MAX_PATH} so a pathological loop can't grow the column
 * unbounded. All methods are null-tolerant and never throw — a corrupt column
 * degrades to an empty path rather than breaking a turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurChatFlowNodeVisitPath {

    /** Hard ceiling on retained node visits per conversation. */
    public static final int MAX_PATH = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    private TurChatFlowNodeVisitPath() {
    }

    /** Parses the stored JSON array into a mutable list; empty on null/blank/corrupt. */
    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = MAPPER.readValue(json, LIST_TYPE);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Returns the JSON array that results from appending {@code nodeId} to the
     * path encoded in {@code currentJson}. A blank node id, a consecutive
     * duplicate, or a path already at {@link #MAX_PATH} leaves the path
     * unchanged (returns a normalized serialization of the existing path).
     */
    public static String append(String currentJson, String nodeId) {
        List<String> path = parse(currentJson);
        if (nodeId == null || nodeId.isBlank()) {
            return serialize(path);
        }
        if (!path.isEmpty() && path.get(path.size() - 1).equals(nodeId)) {
            return serialize(path);
        }
        if (path.size() >= MAX_PATH) {
            return serialize(path);
        }
        path.add(nodeId);
        return serialize(path);
    }

    /** Serializes a path list to a JSON array string; {@code "[]"} on failure. */
    public static String serialize(List<String> path) {
        try {
            return MAPPER.writeValueAsString(path == null ? List.of() : path);
        } catch (RuntimeException e) {
            return "[]";
        }
    }
}
