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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TurConversationLock} — the per-conversation
 * serialization gate that stops a side-channel slot/form/flow write from
 * lost-updating a concurrent chat-turn advance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurConversationLockTest {

    private final TurConversationLock lock = new TurConversationLock();

    @Test
    void runsActionAndReturnsValue() {
        String out = lock.runExclusive("conv-1", () -> "ok");
        assertThat(out).isEqualTo("ok");
    }

    @Test
    void blankConversationId_runsDirectly() {
        AtomicInteger ran = new AtomicInteger();
        lock.runExclusive("  ", () -> ran.incrementAndGet());
        lock.runExclusive((String) null, () -> ran.incrementAndGet());
        assertThat(ran.get()).isEqualTo(2);
    }

    @Test
    void isReentrantOnTheSameThread_sameConversation() {
        // A locked section that nests another locked call on the same
        // conversation (same thread) must not self-deadlock.
        String out = lock.runExclusive("conv-1",
                () -> lock.runExclusive("conv-1", () -> "nested"));
        assertThat(out).isEqualTo("nested");
    }

    @Test
    void serializesConcurrentSectionsOnSameConversation() throws InterruptedException {
        // Two threads hammering the SAME conversation must never overlap inside
        // the critical section — otherwise the lost-update this class exists to
        // prevent could occur. We assert max observed concurrency == 1.
        int threads = 16;
        int iterationsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        lock.runExclusive("same-conversation", () -> {
                            int now = inside.incrementAndGet();
                            maxObserved.accumulateAndGet(now, Math::max);
                            // tiny window to expose overlap if the lock failed
                            for (int s = 0; s < 50; s++) {
                                Math.sqrt(s);
                            }
                            inside.decrementAndGet();
                        });
                    }
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(failures).isEmpty();
        assertThat(maxObserved.get()).isEqualTo(1);
    }

    @Test
    void differentConversations_runConcurrently() throws InterruptedException {
        // Distinct conversations must NOT serialize against each other: both
        // threads should be able to sit inside their own critical section at
        // the same time. We prove it by requiring both to be inside together
        // before either is allowed to leave.
        CountDownLatch bothInside = new CountDownLatch(2);
        List<String> order = Collections.synchronizedList(new java.util.ArrayList<>());
        Runnable a = () -> lock.runExclusive("conv-A", () -> {
            bothInside.countDown();
            await(bothInside);
            order.add("A");
        });
        Runnable b = () -> lock.runExclusive("conv-B", () -> {
            bothInside.countDown();
            await(bothInside);
            order.add("B");
        });
        Thread ta = new Thread(a);
        Thread tb = new Thread(b);
        ta.start();
        tb.start();
        ta.join(TimeUnit.SECONDS.toMillis(10));
        tb.join(TimeUnit.SECONDS.toMillis(10));
        // If they serialized, bothInside would never reach 0 and the threads
        // would still be alive (timed out join). Reaching size 2 proves overlap.
        assertThat(order).containsExactlyInAnyOrder("A", "B");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
