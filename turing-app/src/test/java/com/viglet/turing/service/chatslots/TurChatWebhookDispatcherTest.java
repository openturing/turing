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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurChatWebhook;

/**
 * Drives {@link TurChatWebhookDispatcher#onSlotEvent} synchronously (the
 * executor is never started, so {@code fire} runs inline) to pin the
 * edge-detection contract: one POST per distinct value transition of a
 * matching trigger slot — never on unchanged re-publishes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatWebhookDispatcherTest {

    @Mock
    private TurChatSlotEventBus slotEventBus;
    @Mock
    private TurChatWebhookService webhookService;

    private TurChatWebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new TurChatWebhookDispatcher(slotEventBus, webhookService);
    }

    private static TurChatWebhook webhook(String name, String trigger) {
        TurChatWebhook w = new TurChatWebhook();
        w.setName(name);
        w.setTargetUrl("https://crm.example.com/hook");
        w.setSlotTrigger(trigger);
        w.setEnabled(true);
        return w;
    }

    private static TurChatSessionSlotsDto event(String conv, Map<String, String> slots) {
        return new TurChatSessionSlotsDto(conv, slots);
    }

    @Test
    void firesOnceWhenTriggerSlotFirstAppears() {
        TurChatWebhook hook = webhook("push_crm", "email");
        when(webhookService.findSlotTriggerWebhooks()).thenReturn(List.of(hook));

        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "ada@x.com")));

        verify(webhookService, times(1))
                .dispatchSlotWrite(eq(hook), eq("conv-1"), eq("email"), any());
    }

    @Test
    void doesNotRefireOnUnchangedRepublish() {
        TurChatWebhook hook = webhook("push_crm", "email");
        when(webhookService.findSlotTriggerWebhooks()).thenReturn(List.of(hook));

        Map<String, String> slots = Map.of("email", "ada@x.com");
        dispatcher.onSlotEvent(event("conv-1", slots));
        dispatcher.onSlotEvent(event("conv-1", slots)); // identical snapshot

        verify(webhookService, times(1))
                .dispatchSlotWrite(eq(hook), eq("conv-1"), eq("email"), any());
    }

    @Test
    void refiresWhenTriggerValueChanges() {
        TurChatWebhook hook = webhook("push_crm", "email");
        when(webhookService.findSlotTriggerWebhooks()).thenReturn(List.of(hook));

        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "ada@x.com")));
        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "grace@x.com")));

        verify(webhookService, times(2))
                .dispatchSlotWrite(eq(hook), eq("conv-1"), eq("email"), any());
    }

    @Test
    void wildcardFiresForEachChangedSlot() {
        TurChatWebhook hook = webhook("audit", "*");
        when(webhookService.findSlotTriggerWebhooks()).thenReturn(List.of(hook));

        dispatcher.onSlotEvent(event("conv-1", new java.util.LinkedHashMap<>(Map.of(
                "name", "Ada", "email", "ada@x.com"))));

        verify(webhookService).dispatchSlotWrite(eq(hook), eq("conv-1"), eq("name"), any());
        verify(webhookService).dispatchSlotWrite(eq(hook), eq("conv-1"), eq("email"), any());
    }

    @Test
    void nonMatchingTriggerNeverFires() {
        TurChatWebhook hook = webhook("name_hook", "name");
        when(webhookService.findSlotTriggerWebhooks()).thenReturn(List.of(hook));

        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "ada@x.com")));

        verify(webhookService, never()).dispatchSlotWrite(any(), any(), any(), any());
    }

    @Test
    void noWebhooksConfiguredStillTracksSnapshotSoLaterAddDoesNotBackfire() {
        // Round 1: no webhooks declared yet — the slot is recorded as seen.
        when(webhookService.findSlotTriggerWebhooks())
                .thenReturn(List.of())
                .thenReturn(List.of(webhook("push_crm", "email")));

        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "ada@x.com")));
        // Round 2: a webhook now exists, but the same value re-publishes —
        // must NOT backfire for the already-seen value.
        dispatcher.onSlotEvent(event("conv-1", Map.of("email", "ada@x.com")));

        verify(webhookService, never()).dispatchSlotWrite(any(), any(), any(), any());
    }

    @Test
    void nullEventIgnored() {
        dispatcher.onSlotEvent(null);
        verify(webhookService, never()).findSlotTriggerWebhooks();
    }
}
