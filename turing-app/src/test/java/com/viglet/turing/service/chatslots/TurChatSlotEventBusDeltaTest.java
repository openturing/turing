/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto;

import reactor.core.Disposable;

/**
 * Drives {@link TurChatSlotEventBus#subscribeDelta} synchronously — the
 * multicast sink delivers inline on the publishing thread, so a plain
 * collecting subscriber sees events in order without {@code StepVerifier}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatSlotEventBusDeltaTest {

    @Test
    void emitsInitialSnapshotThenIncrementalDeltas() {
        TurChatSlotEventBus bus = new TurChatSlotEventBus();
        List<TurChatSessionSlotsDeltaDto> received = new ArrayList<>();

        Disposable sub = bus.subscribeDelta("conv-1", Map.of("name", "Ada"))
                .subscribe(received::add);

        // First event is delivered eagerly on subscribe — the snapshot.
        assertEquals(1, received.size());
        assertTrue(received.get(0).snapshot());
        assertEquals(Map.of("name", "Ada"), received.get(0).added());

        // A write that adds 'email' → one delta with only the added key.
        bus.publish("conv-1", Map.of("name", "Ada", "email", "a@x.com"));
        assertEquals(2, received.size());
        assertFalse(received.get(1).snapshot());
        assertEquals(Map.of("email", "a@x.com"), received.get(1).added());
        assertTrue(received.get(1).updated().isEmpty());

        // A write that changes 'name' → one delta with only the updated key.
        bus.publish("conv-1", Map.of("name", "Grace", "email", "a@x.com"));
        assertEquals(3, received.size());
        assertEquals(Map.of("name", "Grace"), received.get(2).updated());

        sub.dispose();
    }

    @Test
    void republishingTheSameSnapshotEmitsNoDelta() {
        TurChatSlotEventBus bus = new TurChatSlotEventBus();
        List<TurChatSessionSlotsDeltaDto> received = new ArrayList<>();

        Disposable sub = bus.subscribeDelta("conv-1", Map.of("name", "Ada"))
                .subscribe(received::add);
        assertEquals(1, received.size()); // snapshot only

        // Identical map → empty delta → filtered out, nothing emitted.
        bus.publish("conv-1", Map.of("name", "Ada"));
        assertEquals(1, received.size());

        sub.dispose();
    }

    @Test
    void otherConversationsAreNotMixedIn() {
        TurChatSlotEventBus bus = new TurChatSlotEventBus();
        List<TurChatSessionSlotsDeltaDto> received = new ArrayList<>();

        Disposable sub = bus.subscribeDelta("conv-1", Map.of())
                .subscribe(received::add);
        assertEquals(1, received.size()); // snapshot (empty)

        bus.publish("conv-OTHER", Map.of("x", "1"));
        assertEquals(1, received.size()); // unaffected

        sub.dispose();
    }
}
