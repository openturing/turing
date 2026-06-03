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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.agent.TurChatSlotSseChannelsDto;
import com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode;

/**
 * T90 / §VII.10.g — unit coverage for the SSE channel registry: refcounting,
 * channel reclamation when the last connection leaves, mode separation, and
 * the snapshot aggregate. Instantiated directly (no Spring) since the registry
 * holds no collaborators.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatSlotSseRegistryTest {

    @Test
    void emptyRegistrySnapshotIsZeroed() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        TurChatSlotSseChannelsDto snapshot = registry.snapshot();
        assertThat(snapshot.openChannels()).isZero();
        assertThat(snapshot.openSubscribers()).isZero();
        assertThat(snapshot.distinctConversations()).isZero();
        assertThat(snapshot.channels()).isEmpty();
    }

    @Test
    void refcountsMultipleConnectionsOnOneChannel() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        registry.acquire("conv-1", Mode.SNAPSHOT);
        registry.acquire("conv-1", Mode.SNAPSHOT);
        registry.acquire("conv-1", Mode.SNAPSHOT);

        TurChatSlotSseChannelsDto snapshot = registry.snapshot();
        assertThat(snapshot.openChannels()).isEqualTo(1);
        assertThat(snapshot.openSubscribers()).isEqualTo(3);
        assertThat(snapshot.distinctConversations()).isEqualTo(1);
        assertThat(snapshot.channels()).singleElement().satisfies(c -> {
            assertThat(c.conversationId()).isEqualTo("conv-1");
            assertThat(c.mode()).isEqualTo("snapshot");
            assertThat(c.refcount()).isEqualTo(3);
        });
    }

    @Test
    void channelIsReclaimedWhenLastConnectionLeaves() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        registry.acquire("conv-1", Mode.SNAPSHOT);
        registry.acquire("conv-1", Mode.SNAPSHOT);
        registry.release("conv-1", Mode.SNAPSHOT);

        assertThat(registry.snapshot().openSubscribers()).isEqualTo(1);

        registry.release("conv-1", Mode.SNAPSHOT);
        TurChatSlotSseChannelsDto snapshot = registry.snapshot();
        assertThat(snapshot.openChannels()).isZero();
        assertThat(snapshot.channels()).isEmpty();
    }

    @Test
    void snapshotAndDeltaAreSeparateChannelsForSameConversation() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        registry.acquire("conv-1", Mode.SNAPSHOT);
        registry.acquire("conv-1", Mode.DELTA);

        TurChatSlotSseChannelsDto snapshot = registry.snapshot();
        assertThat(snapshot.openChannels()).isEqualTo(2);
        assertThat(snapshot.openSubscribers()).isEqualTo(2);
        // Two channels, one conversation — mirrors a tab consuming both streams.
        assertThat(snapshot.distinctConversations()).isEqualTo(1);
        assertThat(snapshot.channels()).extracting(TurChatSlotSseChannelsDto.Channel::mode)
                .containsExactlyInAnyOrder("snapshot", "delta");
    }

    @Test
    void countsDistinctConversationsAndConnectionsLikeAPortal() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        // The doc scenario: 12 tabs across 4 conversations, 3 tabs each.
        for (int conv = 0; conv < 4; conv++) {
            for (int tab = 0; tab < 3; tab++) {
                registry.acquire("conv-" + conv, Mode.SNAPSHOT);
            }
        }
        TurChatSlotSseChannelsDto snapshot = registry.snapshot();
        assertThat(snapshot.openChannels()).isEqualTo(4);
        assertThat(snapshot.distinctConversations()).isEqualTo(4);
        assertThat(snapshot.openSubscribers()).isEqualTo(12);
        assertThat(snapshot.channels()).allSatisfy(c -> assertThat(c.refcount()).isEqualTo(3));
    }

    @Test
    void releaseBelowZeroDoesNotGoNegativeOrThrow() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        // Release without a matching acquire — unknown channel, no-op.
        registry.release("ghost", Mode.DELTA);
        assertThat(registry.snapshot().openChannels()).isZero();
    }

    @Test
    void blankConversationIsIgnored() {
        TurChatSlotSseRegistry registry = new TurChatSlotSseRegistry();
        registry.acquire("  ", Mode.SNAPSHOT);
        registry.acquire(null, Mode.SNAPSHOT);
        assertThat(registry.snapshot().openChannels()).isZero();
    }
}
