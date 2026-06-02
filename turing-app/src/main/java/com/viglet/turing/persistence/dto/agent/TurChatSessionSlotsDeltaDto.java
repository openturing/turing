/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T63 / §VII.6.d — a slot-stream <em>delta</em>: only what changed since the
 * previous event, instead of the full {@link TurChatSessionSlotsDto} snapshot.
 * On conversations with 20+ slots this is ~10× fewer bytes on the wire because
 * a single-slot write no longer re-serializes the whole map.
 *
 * <p>Two event shapes:
 * <ul>
 *   <li><b>snapshot</b> ({@link #snapshot()} = {@code true}) — the first event
 *       a subscriber receives, carrying the entire current state in
 *       {@link #added()}. Clients <b>replace</b> their local map with it (so a
 *       reconnect's fresh snapshot resets any drift).</li>
 *   <li><b>delta</b> ({@link #snapshot()} = {@code false}) — incremental:
 *       {@link #added()} + {@link #updated()} are upserts, {@link #removed()}
 *       are deletes. Empty deltas are never emitted.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSessionSlotsDeltaDto(
        String conversationId,
        boolean snapshot,
        Map<String, String> added,
        Map<String, String> updated,
        List<String> removed) {

    /** Full-state event: the whole map lands in {@code added}, flagged as a snapshot. */
    public static TurChatSessionSlotsDeltaDto snapshot(String conversationId,
            Map<String, String> slots) {
        return new TurChatSessionSlotsDeltaDto(conversationId, true,
                slots == null ? Map.of() : new LinkedHashMap<>(slots), Map.of(), List.of());
    }

    /**
     * Computes the incremental delta between two full snapshots:
     * <ul>
     *   <li>{@code added} — keys in {@code after} absent from {@code before}.</li>
     *   <li>{@code updated} — keys in both whose value changed.</li>
     *   <li>{@code removed} — keys in {@code before} absent from {@code after}.</li>
     * </ul>
     */
    public static TurChatSessionSlotsDeltaDto diff(String conversationId,
            Map<String, String> before, Map<String, String> after) {
        Map<String, String> safeBefore = before == null ? Map.of() : before;
        Map<String, String> safeAfter = after == null ? Map.of() : after;
        Map<String, String> added = new LinkedHashMap<>();
        Map<String, String> updated = new LinkedHashMap<>();
        List<String> removed = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : safeAfter.entrySet()) {
            if (!safeBefore.containsKey(e.getKey())) {
                added.put(e.getKey(), e.getValue());
            } else if (!java.util.Objects.equals(safeBefore.get(e.getKey()), e.getValue())) {
                updated.put(e.getKey(), e.getValue());
            }
        }
        for (String key : safeBefore.keySet()) {
            if (!safeAfter.containsKey(key)) {
                removed.add(key);
            }
        }
        return new TurChatSessionSlotsDeltaDto(conversationId, false, added, updated, removed);
    }

    /** A delta carrying no changes — the bus filters these out so the wire stays quiet. */
    public boolean isEmpty() {
        return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
    }
}
