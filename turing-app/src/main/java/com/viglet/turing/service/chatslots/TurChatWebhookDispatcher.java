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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurChatWebhook;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * T62 / §VII.6.c — the "wider slot subscription": admin-declared webhooks
 * fire automatically on arbitrary slot writes. This bean subscribes once to
 * the global {@link TurChatSlotEventBus#subscribeAll()} stream and, for every
 * slot-write event, edge-detects which slots actually changed and POSTs the
 * snapshot to each webhook whose {@code slotTrigger} matches.
 *
 * <p><b>Why edge-detection.</b> The bus carries the full merged slot map per
 * write (not a delta — that's a future T63), so a naive "fire on any event"
 * would re-POST a webhook every turn while its trigger slot merely stays set.
 * We keep a bounded per-conversation snapshot of the last-seen slots and only
 * fire for keys whose value differs from the previous snapshot. Effective
 * result: one POST per distinct value transition of the trigger slot.
 *
 * <p><b>Threading.</b> The bus emits synchronously on the publishing thread
 * (the chat-flow engine's {@code writeSlot}, a Custom Tool, etc.). We never
 * run the outbound HTTP POST on that thread — it is handed to a small fixed
 * pool so a slow CRM can never stall a chat turn.
 *
 * <p><b>Single-node.</b> Like the bus itself, the last-seen map lives
 * in-process. A clustered deploy that routes writes for one conversation to
 * different nodes may double-fire (each node edge-detects independently);
 * acceptable for v1 and documented alongside the bus's own single-node
 * caveat.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurChatWebhookDispatcher {

    /** Cap on tracked conversations to bound memory; LRU-evicts the oldest. */
    private static final int MAX_TRACKED_CONVERSATIONS = 10_000;

    private final TurChatSlotEventBus slotEventBus;
    private final TurChatWebhookService webhookService;

    /**
     * conversationId → last-seen slot map. Access-ordered + size-capped so the
     * least-recently-touched conversation is evicted past the cap. Guarded by
     * its own monitor since bus events may arrive concurrently.
     */
    private final Map<String, Map<String, String>> lastSeen = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, String>> eldest) {
                    return size() > MAX_TRACKED_CONVERSATIONS;
                }
            });

    private ExecutorService executor;
    private Disposable subscription;

    public TurChatWebhookDispatcher(TurChatSlotEventBus slotEventBus,
            TurChatWebhookService webhookService) {
        this.slotEventBus = slotEventBus;
        this.webhookService = webhookService;
    }

    @PostConstruct
    void start() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "tur-webhook-dispatch");
            t.setDaemon(true);
            return t;
        });
        this.subscription = slotEventBus.subscribeAll().subscribe(this::onSlotEvent);
        log.info("[WebhookDispatcher] subscribed to slot event bus");
    }

    @PreDestroy
    void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    /**
     * Reacts to one slot-write event off the bus: edge-detect the changed
     * slots, then fan out to the matching webhooks. Package-private and
     * side-effect-isolated (no executor when called directly) so tests can
     * drive it synchronously.
     */
    void onSlotEvent(TurChatSessionSlotsDto event) {
        if (event == null || event.conversationId() == null) {
            return;
        }
        List<TurChatWebhook> webhooks = webhookService.findSlotTriggerWebhooks();
        // Compute the diff regardless — keeping lastSeen current matters even
        // when no webhook is configured, so a later-added webhook doesn't fire
        // retroactively for slots that were already set.
        List<String> changed = diffAndRecord(event.conversationId(), event.slots());
        if (webhooks.isEmpty() || changed.isEmpty()) {
            return;
        }
        Map<String, String> slots = event.slots();
        for (String slot : changed) {
            for (TurChatWebhook webhook : webhooks) {
                if (TurChatWebhookService.matchesSlot(webhook, slot)) {
                    fire(webhook, event.conversationId(), slot, slots);
                }
            }
        }
    }

    /** Hands the POST to the pool; falls back to inline when no pool (tests). */
    private void fire(TurChatWebhook webhook, String conversationId, String slot,
            Map<String, String> slots) {
        Runnable task = () -> webhookService.dispatchSlotWrite(webhook, conversationId, slot, slots);
        if (executor != null) {
            executor.submit(task);
        } else {
            task.run();
        }
    }

    /**
     * Returns the slot names whose value changed (added or updated) versus the
     * previous snapshot for this conversation, then records the new snapshot.
     * Removed slots are not treated as a fireable change.
     */
    private List<String> diffAndRecord(String conversationId, Map<String, String> current) {
        Map<String, String> safeCurrent = current == null ? Map.of() : current;
        List<String> changed = new ArrayList<>();
        synchronized (lastSeen) {
            Map<String, String> previous = lastSeen.get(conversationId);
            for (Map.Entry<String, String> entry : safeCurrent.entrySet()) {
                String prevValue = previous == null ? null : previous.get(entry.getKey());
                if (!Objects.equals(prevValue, entry.getValue())) {
                    changed.add(entry.getKey());
                }
            }
            lastSeen.put(conversationId, new LinkedHashMap<>(safeCurrent));
        }
        return changed;
    }
}
