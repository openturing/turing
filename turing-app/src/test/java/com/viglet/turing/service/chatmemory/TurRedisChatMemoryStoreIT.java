/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Jedis;

/**
 * End-to-end coverage for {@link TurRedisChatMemoryStore} against a real Redis
 * (Testcontainers): append turns, read back the most-recent tail, and verify
 * the capped-list retention behavior.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurRedisChatMemoryStoreIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

    private TurRedisChatMemoryStore store;

    @BeforeAll
    void startContainer() {
        REDIS.start();
    }

    @AfterAll
    void stopContainer() {
        if (store != null) {
            store.close();
        }
        REDIS.stop();
    }

    @BeforeEach
    void reset() {
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            jedis.flushAll();
        }
        String uri = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        store = new TurRedisChatMemoryStore(uri, "turmem");
    }

    private TurChatMemoryEvent turn(String conv, String user, String assistant, int maxMessages) {
        return new TurChatMemoryEvent("site", "agent-1", conv, "en", user, assistant, T0, maxMessages);
    }

    @Test
    void engineAndEnabled() {
        assertThat(store.isEnabled()).isTrue();
        assertThat(store.getEngine()).isEqualTo(TurChatMemoryEngine.REDIS);
    }

    @Test
    void appendAndReadBackInOrder() {
        // Each turn is stored as two entries: a "user" then an "assistant" message.
        store.appendBatch("c1", List.of(
                turn("c1", "hi", "hello", 50),
                turn("c1", "how are you", "great", 50)));

        List<Map<String, Object>> messages = store.findMessages("c1", 50);
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "hi");
        assertThat(messages.get(1)).containsEntry("role", "assistant").containsEntry("content", "hello");
        assertThat(messages.get(3)).containsEntry("content", "great");
    }

    @Test
    void findMessagesReturnsMostRecentTail() {
        for (int i = 0; i < 10; i++) {
            store.appendBatch("c2", List.of(turn("c2", "u" + i, "a" + i, 100)));
        }
        // 20 entries total; the last (newest) entry is assistant "a9".
        List<Map<String, Object>> tail = store.findMessages("c2", 3);
        assertThat(tail).hasSize(3);
        assertThat(tail.get(2)).containsEntry("role", "assistant").containsEntry("content", "a9");
    }

    @Test
    void cappedListDropsOldestBeyondMaxMessages() {
        // maxMessages=4 caps the entry list to 4 → only the last two turns survive.
        for (int i = 0; i < 8; i++) {
            store.appendBatch("c3", List.of(turn("c3", "u" + i, "a" + i, 4)));
        }
        List<Map<String, Object>> kept = store.findMessages("c3", 100);
        assertThat(kept).hasSize(4);
        assertThat(kept.get(0)).containsEntry("content", "u6");
        assertThat(kept.get(3)).containsEntry("content", "a7");
    }

    @Test
    void emptyConversationReturnsEmpty() {
        assertThat(store.findMessages("nope", 10)).isEmpty();
    }
}
