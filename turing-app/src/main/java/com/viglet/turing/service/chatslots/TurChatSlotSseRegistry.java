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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.dto.agent.TurChatSlotSseChannelsDto;

/**
 * T90 / §VII.10.g — refcounted registry of open slot-stream SSE channels on
 * this node. The slot-stream endpoints ({@code GET /chat/slots/stream} and its
 * {@code /delta} variant, T54/T63) open one reactive subscription per HTTP
 * connection; this registry counts those connections per
 * {@code (conversationId, mode)} channel so the admin debug surface can render
 * a live "open channels + refcount" panel.
 *
 * <p>It is the server-side counterpart to the vanilla SDK's
 * {@code _slotsSseOpenChannelCount()}: the SDK multiplexer collapses N
 * subscriptions in one browser onto a single {@code EventSource}, so a portal
 * with 12 tabs across 4 conversations shows up here as 4 channels with refcount
 * summing to 12 (assuming no per-browser multiplexing). The operator validates
 * the multiplexing is behaving — channels are reclaimed when the last tab
 * closes, refcounts don't leak.
 *
 * <p>Per-process and ephemeral: clustered deploys keep one registry per node
 * (the slot bus is itself single-node, see
 * {@link TurChatSlotEventBus}); a restart clears it. Acquire/release are
 * lock-free per key via {@code ConcurrentHashMap.compute}; the snapshot is a
 * weakly-consistent point-in-time read, which is exactly the contract a debug
 * panel needs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurChatSlotSseRegistry {

    /** Stream variant a channel serves — mirrors the SDK's key suffix. */
    public enum Mode {
        SNAPSHOT, DELTA,
        /** T113 — the per-conversation workspace artifact stream. */
        WORKSPACE,
        /** T120 — the per-conversation spectator message stream. */
        SPECTATE;

        /** Lowercase wire token used in the DTO and the SDK key. */
        public String wire() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Mutable per-channel state. {@code refcount} is an {@link AtomicInteger}
     * so the snapshot path can read it safely outside the {@code compute}
     * lock; lifecycle transitions (create / drop to zero) happen only inside
     * {@code compute}, which serializes per key.
     */
    private static final class ChannelState {
        private final String conversationId;
        private final Mode mode;
        private final long firstOpenedEpochMillis;
        private volatile long lastChangeEpochMillis;
        private final AtomicInteger refcount = new AtomicInteger();

        private ChannelState(String conversationId, Mode mode, long now) {
            this.conversationId = conversationId;
            this.mode = mode;
            this.firstOpenedEpochMillis = now;
            this.lastChangeEpochMillis = now;
        }
    }

    private final ConcurrentMap<String, ChannelState> channels = new ConcurrentHashMap<>();

    private static String keyFor(String conversationId, Mode mode) {
        return conversationId + '|' + mode.name();
    }

    /**
     * Registers a newly-opened connection on the {@code (conversationId, mode)}
     * channel, creating the channel if this is the first connection. No-op for
     * a blank conversation id. Call from {@code doOnSubscribe} on the stream
     * Flux.
     */
    public void acquire(String conversationId, Mode mode) {
        if (conversationId == null || conversationId.isBlank() || mode == null) {
            return;
        }
        long now = System.currentTimeMillis();
        channels.compute(keyFor(conversationId, mode), (k, existing) -> {
            ChannelState state = existing != null
                    ? existing
                    : new ChannelState(conversationId, mode, now);
            state.refcount.incrementAndGet();
            state.lastChangeEpochMillis = now;
            return state;
        });
    }

    /**
     * Releases a closed connection on the channel, dropping the channel
     * entirely when the last connection leaves. No-op for a blank conversation
     * id or an unknown channel. Call from {@code doFinally} on the stream Flux
     * (fires on complete / error / cancel — cancel being the usual SSE
     * disconnect).
     */
    public void release(String conversationId, Mode mode) {
        if (conversationId == null || conversationId.isBlank() || mode == null) {
            return;
        }
        long now = System.currentTimeMillis();
        channels.computeIfPresent(keyFor(conversationId, mode), (k, existing) -> {
            int remaining = existing.refcount.decrementAndGet();
            existing.lastChangeEpochMillis = now;
            return remaining <= 0 ? null : existing;
        });
    }

    /**
     * Point-in-time snapshot of every open channel, sorted by most-recent
     * change first. Weakly consistent — concurrent acquires/releases during
     * iteration are tolerated, which is acceptable for a debug surface.
     */
    public TurChatSlotSseChannelsDto snapshot() {
        List<TurChatSlotSseChannelsDto.Channel> rows = new ArrayList<>(channels.size());
        int subscribers = 0;
        Set<String> conversations = new HashSet<>();
        for (ChannelState state : channels.values()) {
            int refcount = state.refcount.get();
            if (refcount <= 0) {
                // Mid-removal race — skip a channel already dropping to zero.
                continue;
            }
            subscribers += refcount;
            conversations.add(state.conversationId);
            rows.add(new TurChatSlotSseChannelsDto.Channel(
                    state.conversationId,
                    state.mode.wire(),
                    refcount,
                    state.firstOpenedEpochMillis,
                    state.lastChangeEpochMillis));
        }
        rows.sort(Comparator.comparingLong(
                TurChatSlotSseChannelsDto.Channel::lastChangeEpochMillis).reversed());
        return new TurChatSlotSseChannelsDto(rows.size(), subscribers, conversations.size(), rows);
    }
}
