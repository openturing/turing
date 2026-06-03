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

import java.util.List;

/**
 * T90 / §VII.10.g — live snapshot of the open slot-stream SSE channels on this
 * node, the server-side counterpart to the vanilla SDK's
 * {@code _slotsSseOpenChannelCount()}. The SDK collapses N
 * {@code createSlotsController({ transport: "sse" })} subscriptions in one
 * browser onto a single {@code EventSource}; the server sees one Flux
 * subscription per connection. This DTO exposes how many distinct
 * {@code (conversationId, mode)} channels are open and the per-channel
 * connection refcount, so an operator can validate "12 portal tabs, 4
 * channels, refcount sane" without attaching a profiler.
 *
 * @param openChannels         distinct {@code (conversationId, mode)} channels open
 * @param openSubscribers      sum of every channel's refcount — the total number
 *                             of live SSE connections on this node
 * @param distinctConversations distinct conversation ids across both modes
 * @param channels             per-channel detail, newest-change first
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSlotSseChannelsDto(
        int openChannels,
        int openSubscribers,
        int distinctConversations,
        List<Channel> channels) {

    /**
     * One open channel — a {@code (conversationId, mode)} pair with the number
     * of live connections subscribed to it.
     *
     * @param conversationId         the conversation the stream is scoped to
     * @param mode                   {@code "snapshot"} or {@code "delta"} (T63)
     * @param refcount               number of live SSE connections on this channel
     * @param firstOpenedEpochMillis when the first connection opened the channel
     * @param lastChangeEpochMillis  last acquire/release on this channel
     */
    public record Channel(
            String conversationId,
            String mode,
            int refcount,
            long firstOpenedEpochMillis,
            long lastChangeEpochMillis) {
    }
}
