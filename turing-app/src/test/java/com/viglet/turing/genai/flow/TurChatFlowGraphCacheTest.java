/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.viglet.testsupport.cache.TurChatFlowGraphCacheTestConfig;

/**
 * T487 — verifies the {@code @Cacheable} memoization on {@link TurChatFlowGraphCache}
 * plus the {@link java.io.Serializable} contract the clustered (Hazelcast) cache
 * relies on.
 *
 * <p>Boots a tiny annotation-config context with {@code @EnableCaching} + a
 * {@link ConcurrentMapCacheManager} so the Spring cache proxy is actually in the
 * path — a plain {@code new} of the bean would bypass it. Asserts the same flow
 * id reuses the parsed graph (the {@link Supplier} runs once) while a different
 * id recomputes, and that an unparseable flow ({@code null}) is not pinned.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatFlowGraphCacheTest {

    private AnnotationConfigApplicationContext context;
    private TurChatFlowGraphCache cache;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TurChatFlowGraphCacheTestConfig.class);
        cache = context.getBean(TurChatFlowGraphCache.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static ChatFlowGraph sampleGraph() {
        return new ChatFlowGraph(
                List.of(new ChatFlowNode("start", "start", null)),
                List.of(new ChatFlowEdge("e1", "start", "n2", null, null, "next")));
    }

    @Test
    void sameFlowIdSkipsReparse() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<ChatFlowGraph> parse = () -> {
            calls.incrementAndGet();
            return sampleGraph();
        };

        ChatFlowGraph first = cache.graph("flowA", parse);
        ChatFlowGraph second = cache.graph("flowA", parse);

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
        assertThat(calls.get()).as("second call for the same flow id must hit the cache").isEqualTo(1);
    }

    @Test
    void differentFlowIdReparses() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<ChatFlowGraph> parse = () -> {
            calls.incrementAndGet();
            return sampleGraph();
        };

        cache.graph("flowA", parse);
        cache.graph("flowB", parse);

        assertThat(calls.get()).as("a different flow id is a new key").isEqualTo(2);
    }

    @Test
    void nullResultIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<ChatFlowGraph> parse = () -> {
            calls.incrementAndGet();
            return null;
        };

        cache.graph("blankFlow", parse);
        cache.graph("blankFlow", parse);

        assertThat(calls.get()).as("an unparseable (null) graph must not be pinned").isEqualTo(2);
    }

    @Test
    void parsedGraphIsSerializableForClusteredCache() throws Exception {
        ChatFlowGraph graph = sampleGraph();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(graph);
        }
        ChatFlowGraph roundTripped;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            roundTripped = (ChatFlowGraph) ois.readObject();
        }

        assertThat(roundTripped).isEqualTo(graph);
    }
}
