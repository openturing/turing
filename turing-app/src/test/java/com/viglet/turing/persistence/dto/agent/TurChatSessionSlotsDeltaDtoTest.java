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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure-function tests for the T63 slot delta computation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatSessionSlotsDeltaDtoTest {

    @Test
    void snapshotPutsWholeMapInAddedAndFlagsIt() {
        TurChatSessionSlotsDeltaDto d = TurChatSessionSlotsDeltaDto.snapshot(
                "conv-1", Map.of("name", "Ada", "email", "a@x.com"));
        assertTrue(d.snapshot());
        assertEquals(2, d.added().size());
        assertTrue(d.updated().isEmpty());
        assertTrue(d.removed().isEmpty());
        assertFalse(d.isEmpty());
    }

    @Test
    void diffDetectsAddedUpdatedRemoved() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("name", "Ada");
        before.put("city", "Rio");
        Map<String, String> after = new LinkedHashMap<>();
        after.put("name", "Grace");   // updated
        after.put("email", "g@x.com"); // added
        // city removed

        TurChatSessionSlotsDeltaDto d = TurChatSessionSlotsDeltaDto.diff("conv-1", before, after);

        assertFalse(d.snapshot());
        assertEquals(Map.of("email", "g@x.com"), d.added());
        assertEquals(Map.of("name", "Grace"), d.updated());
        assertEquals(java.util.List.of("city"), d.removed());
        assertFalse(d.isEmpty());
    }

    @Test
    void diffOfIdenticalMapsIsEmpty() {
        Map<String, String> same = Map.of("a", "1", "b", "2");
        TurChatSessionSlotsDeltaDto d = TurChatSessionSlotsDeltaDto.diff("conv-1", same, same);
        assertTrue(d.isEmpty());
        assertTrue(d.added().isEmpty());
        assertTrue(d.updated().isEmpty());
        assertTrue(d.removed().isEmpty());
    }

    @Test
    void diffTreatsNullMapsAsEmpty() {
        TurChatSessionSlotsDeltaDto added = TurChatSessionSlotsDeltaDto.diff("c", null, Map.of("x", "1"));
        assertEquals(Map.of("x", "1"), added.added());

        TurChatSessionSlotsDeltaDto removed = TurChatSessionSlotsDeltaDto.diff("c", Map.of("x", "1"), null);
        assertEquals(java.util.List.of("x"), removed.removed());

        assertTrue(TurChatSessionSlotsDeltaDto.diff("c", null, null).isEmpty());
    }
}
