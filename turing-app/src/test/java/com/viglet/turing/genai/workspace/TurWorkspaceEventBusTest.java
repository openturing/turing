/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import reactor.core.Disposable;

/**
 * Drives {@link TurWorkspaceEventBus} synchronously — the multicast sink
 * delivers inline on the publishing thread, so a plain collecting subscriber
 * sees events in order without {@code StepVerifier}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurWorkspaceEventBusTest {

    @Test
    void subscriberReceivesEventsForItsConversationOnly() {
        TurWorkspaceEventBus bus = new TurWorkspaceEventBus();
        List<TurWorkspaceEvent> received = new ArrayList<>();

        Disposable sub = bus.subscribe("conv-1").subscribe(received::add);

        bus.publish(TurWorkspaceEvent.put("conv-1", "a.csv", "text/csv", 12, "/api/v2/workspace/file?x"));
        bus.publish(TurWorkspaceEvent.put("conv-OTHER", "b.csv", "text/csv", 5, "/url"));
        bus.publish(TurWorkspaceEvent.delete("conv-1", "a.csv"));

        assertEquals(2, received.size());
        assertEquals(TurWorkspaceEvent.PUT, received.get(0).event());
        assertEquals("a.csv", received.get(0).key());
        assertEquals(TurWorkspaceEvent.DELETE, received.get(1).event());
        sub.dispose();
    }

    @Test
    void deleteEventCarriesOnlyKey() {
        TurWorkspaceEvent ev = TurWorkspaceEvent.delete("conv-1", "reports/x.csv");
        assertEquals(TurWorkspaceEvent.DELETE, ev.event());
        assertEquals("reports/x.csv", ev.key());
        assertEquals(0L, ev.size());
        assertTrue(ev.contentType() == null && ev.signedUrl() == null);
    }

    @Test
    void blankConversationAndNullEventAreIgnored() {
        TurWorkspaceEventBus bus = new TurWorkspaceEventBus();
        List<TurWorkspaceEvent> received = new ArrayList<>();
        Disposable sub = bus.subscribeAll().subscribe(received::add);

        bus.publish(null);
        bus.publish(TurWorkspaceEvent.put("  ", "a.csv", "text/csv", 1, "/u"));

        assertTrue(received.isEmpty());
        sub.dispose();
    }
}
