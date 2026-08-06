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

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * T901 / T903 — runs a cache eviction when the write it invalidates becomes visible to
 * other threads: after commit if a transaction is in progress, inline if none is.
 *
 * <p>
 * Evicting <em>inside</em> the writing transaction is wrong in both directions, which is
 * why this exists rather than each caller deciding. A concurrent reader that arrives
 * between the eviction and the commit re-caches the pre-commit state, and the entry then
 * outlives the write — that is the staleness. Less obviously, a read on the
 * <em>writing</em> thread after the eviction rebuilds the entry from that transaction's
 * own uncommitted state and publishes it to every other thread, so a rollback can leave
 * the cache holding a value that never existed.
 * </p>
 *
 * <p>
 * It is a shared component and not a helper copied per evictor on purpose. The search
 * cache and the snapshot caches had the same defect and were fixed a task apart, because
 * two listeners with the same lifecycle annotations and different visibility semantics
 * gave nothing to compare against. There is one seam now, so the next cache is either
 * routed through it or visibly is not.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurCacheEvictionScheduler {

    /**
     * Cache names with a whole-cache clear already registered for the current
     * transaction. One synchronization is registered for the set, not one per name, and
     * the thread-local is released in {@code afterCompletion}.
     */
    private static final ThreadLocal<Set<String>> pendingClears = new ThreadLocal<>();

    private final CacheManager cacheManager;

    public TurCacheEvictionScheduler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Runs {@code eviction} after the current transaction commits, or immediately when
     * none is active. Never runs it on rollback: a write that did not happen invalidates
     * nothing, and discarding good entries for it is a cost with no purchase.
     */
    public void whenVisible(Runnable eviction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eviction.run();
                    }
                });
    }

    /**
     * Clears {@code cacheName} once the write is visible, and at most once per
     * transaction however many writes ask for it — an admin edit touching forty rows
     * fires its listener forty times, and one clear invalidates what all forty would.
     */
    public void clearOnce(String cacheName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearNow(cacheName);
            return;
        }
        Set<String> pending = pendingClears.get();
        if (pending == null) {
            pending = new LinkedHashSet<>();
            pendingClears.set(pending);
            registerFlush(pending);
        }
        pending.add(cacheName);
    }

    private void registerFlush(Set<String> pending) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pending.forEach(TurCacheEvictionScheduler.this::clearNow);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        pendingClears.remove();
                    }
                });
    }

    private void clearNow(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        cache.clear();
        log.debug("Cleared the '{}' cache", cacheName);
    }
}
