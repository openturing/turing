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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end coverage for {@link TurMongoChatMemoryStore} against a real
 * MongoDB (Testcontainers): append turns, read the most-recent tail, and
 * verify the {@code $push/$slice} capped-array retention.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurMongoChatMemoryStoreIT {

    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    private static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private TurMongoChatMemoryStore store;

    @BeforeAll
    void startContainer() {
        MONGO.start();
    }

    @AfterAll
    void stopContainer() {
        if (store != null) {
            store.close();
        }
        MONGO.stop();
    }

    @BeforeEach
    void reset() {
        store = new TurMongoChatMemoryStore(MONGO.getConnectionString(), "turtest",
                "memory_" + SEQ.incrementAndGet());
    }

    private TurChatMemoryEvent turn(String conv, String user, String assistant, int maxMessages) {
        return new TurChatMemoryEvent("site", "agent-1", conv, "en", user, assistant, T0, maxMessages);
    }

    @Test
    void engineAndEnabled() {
        assertThat(store.isEnabled()).isTrue();
        assertThat(store.getEngine()).isEqualTo(TurChatMemoryEngine.MONGODB);
    }

    @Test
    void appendAndReadBackInOrder() {
        store.appendBatch("c1", List.of(
                turn("c1", "hi", "hello", 50),
                turn("c1", "how are you", "great", 50)));
        List<Map<String, Object>> messages = store.findMessages("c1", 50);
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).containsEntry("role", "user").containsEntry("content", "hi");
        assertThat(messages.get(3)).containsEntry("content", "great");
    }

    @Test
    void cappedArrayKeepsMostRecentEntries() {
        for (int i = 0; i < 8; i++) {
            store.appendBatch("c2", List.of(turn("c2", "u" + i, "a" + i, 4)));
        }
        List<Map<String, Object>> kept = store.findMessages("c2", 100);
        assertThat(kept).hasSize(4);
        assertThat(kept.get(0)).containsEntry("content", "u6");
        assertThat(kept.get(3)).containsEntry("content", "a7");
    }

    @Test
    void emptyConversationReturnsEmpty() {
        assertThat(store.findMessages("nope", 10)).isEmpty();
    }
}
