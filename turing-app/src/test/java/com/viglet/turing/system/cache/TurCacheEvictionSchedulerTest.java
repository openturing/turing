/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * T901 / T903 — the one seam both eviction paths run through: nothing is discarded
 * until the write that invalidates it is visible.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCacheEvictionSchedulerTest {

    private static final String CACHE_A = "cacheA";
    private static final String CACHE_B = "cacheB";

    private final CountingCacheManager cacheManager = new CountingCacheManager();
    private final TurCacheEvictionScheduler scheduler = new TurCacheEvictionScheduler(cacheManager);

    @AfterEach
    void clearAnyTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void inTransaction(Runnable body, boolean commit) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
            List<TransactionSynchronization> synchronizations =
                    new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
            if (commit) {
                synchronizations.forEach(TransactionSynchronization::afterCommit);
            }
            int status = commit ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK;
            synchronizations.forEach(s -> s.afterCompletion(status));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutATransactionTheEvictionIsImmediate() {
        AtomicInteger ran = new AtomicInteger();

        scheduler.whenVisible(ran::incrementAndGet);

        assertEquals(1, ran.get());
    }

    @Test
    void insideATransactionTheEvictionWaitsForTheCommit() {
        AtomicInteger ran = new AtomicInteger();

        inTransaction(() -> {
            scheduler.whenVisible(ran::incrementAndGet);
            assertEquals(0, ran.get(), "the write it invalidates is not visible yet");
        }, true);

        assertEquals(1, ran.get());
    }

    /** A write that did not happen invalidates nothing. */
    @Test
    void aRollbackEvictsNothing() {
        AtomicInteger ran = new AtomicInteger();

        inTransaction(() -> scheduler.whenVisible(ran::incrementAndGet), false);

        assertEquals(0, ran.get());
    }

    @Test
    void clearOnceWaitsForTheCommit() {
        inTransaction(() -> {
            scheduler.clearOnce(CACHE_A);
            assertEquals(0, cacheManager.clears(CACHE_A));
        }, true);

        assertEquals(1, cacheManager.clears(CACHE_A));
    }

    /** Forty writes in one transaction, one clear. */
    @Test
    void clearOnceIsOncePerTransaction() {
        inTransaction(() -> {
            for (int i = 0; i < 40; i++) {
                scheduler.clearOnce(CACHE_A);
            }
        }, true);

        assertEquals(1, cacheManager.clears(CACHE_A));
    }

    /** Distinct caches each get their clear, from a single registration. */
    @Test
    void clearOnceKeepsCachesApart() {
        inTransaction(() -> {
            scheduler.clearOnce(CACHE_A);
            scheduler.clearOnce(CACHE_B);
            scheduler.clearOnce(CACHE_A);
        }, true);

        assertEquals(1, cacheManager.clears(CACHE_A));
        assertEquals(1, cacheManager.clears(CACHE_B));
    }

    @Test
    void clearOnceOnRollbackClearsNothing() {
        inTransaction(() -> scheduler.clearOnce(CACHE_A), false);

        assertEquals(0, cacheManager.clears(CACHE_A));
    }

    /** The dedupe is scoped to the transaction, not to the thread forever. */
    @Test
    void aLaterTransactionClearsAgain() {
        inTransaction(() -> scheduler.clearOnce(CACHE_A), true);
        inTransaction(() -> scheduler.clearOnce(CACHE_A), true);

        assertEquals(2, cacheManager.clears(CACHE_A));
    }

    /** A rolled-back transaction must still release the dedupe state. */
    @Test
    void aRollbackDoesNotStrandTheDedupeFlag() {
        inTransaction(() -> scheduler.clearOnce(CACHE_A), false);
        inTransaction(() -> scheduler.clearOnce(CACHE_A), true);

        assertEquals(1, cacheManager.clears(CACHE_A));
    }

    @Test
    void anUnknownCacheNameIsHarmless() {
        assertNull(cacheManager.getCache("noSuchCache"),
                "the fixture must not auto-create, or this asserts nothing");

        scheduler.clearOnce("noSuchCache");

        assertEquals(0, cacheManager.clears("noSuchCache"));
    }

    /** A cache manager that counts clears per cache name. */
    private static final class CountingCacheManager extends ConcurrentMapCacheManager {
        /**
         * Deliberately no field initializer: {@code super(CACHE_A, CACHE_B)} creates the
         * fixed caches from within the superclass constructor, which runs
         * <em>before</em> this subclass's field initializers — so an assignment here
         * would wipe the counters the callback had already registered.
         */
        private java.util.Map<String, AtomicInteger> counts;

        private CountingCacheManager() {
            // Fixed names: an unknown name must resolve to null so clearOnce can skip it.
            super(CACHE_A, CACHE_B);
        }

        private java.util.Map<String, AtomicInteger> counts() {
            if (counts == null) {
                counts = new java.util.concurrent.ConcurrentHashMap<>();
            }
            return counts;
        }

        int clears(String name) {
            AtomicInteger counter = counts().get(name);
            return counter == null ? 0 : counter.get();
        }

        @Override
        protected org.springframework.cache.Cache createConcurrentMapCache(String name) {
            org.springframework.cache.Cache delegate = super.createConcurrentMapCache(name);
            AtomicInteger counter = counts().computeIfAbsent(name, key -> new AtomicInteger());
            return new org.springframework.cache.support.AbstractValueAdaptingCache(true) {
                @Override
                public String getName() {
                    return delegate.getName();
                }

                @Override
                public Object getNativeCache() {
                    return delegate.getNativeCache();
                }

                @Override
                protected Object lookup(Object key) {
                    ValueWrapper wrapper = delegate.get(key);
                    return wrapper == null ? null : wrapper.get();
                }

                @Override
                public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
                    return delegate.get(key, valueLoader);
                }

                @Override
                public void put(Object key, Object value) {
                    delegate.put(key, value);
                }

                @Override
                public void evict(Object key) {
                    delegate.evict(key);
                }

                @Override
                public void clear() {
                    counter.incrementAndGet();
                    delegate.clear();
                }
            };
        }
    }
}
