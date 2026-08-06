/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.snapshot;

import java.util.List;
import java.util.Map;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.tool.TurSNSiteConfigCache;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T905 / §LXVIII.12 — the backstop sweep for the two SN read-model caches.
 *
 * <p>
 * {@code searchAPI} has had one since it existed, and Block BU's work turned it from the
 * only eviction into a bound on how long a missed one can hurt. These two had no bound at
 * all: their correctness rested entirely on the eviction path being right — the assumption
 * that was wrong three separate times in this block — and a snapshot that went stale for a
 * reason nobody predicted stayed stale until the next write to any SN entity, which on a
 * settled site may be never. The only recovery was a restart.
 * </p>
 *
 * <p>
 * <b>What this deliberately does not claim.</b> The sweep bounds staleness; it cannot
 * detect it. An entry it clears is almost always a live one, not a wrong one, so the count
 * it logs is not a defect count and must not be read as one. That distinction is the whole
 * reason this was argued against for several tasks: a periodic clear turns a bug that
 * would announce itself as permanent staleness into staleness that mysteriously heals, and
 * the second is harder to diagnose. It is built now because the bound is worth more than
 * the signal once the eviction paths stopped changing every task — but the log says
 * "backstop", in those words, so nobody reads a healthy sweep as evidence of a problem or
 * a busy one as evidence of health.
 * </p>
 *
 * <p>
 * Not routed through {@code TurCacheEvictionScheduler}: that seam exists to defer an
 * eviction until the transaction that caused it commits, and a scheduled sweep is caused
 * by nothing and runs in no transaction.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSNSnapshotCacheSweep {

    /** The two caches projecting the same SN site + field graph. */
    private static final List<String> SWEPT = List.of(
            TurSNSiteSearchSnapshotService.CACHE_NAME, TurSNSiteConfigCache.FIELD_CONFIG_CACHE);

    private final CacheManager cacheManager;

    public TurSNSnapshotCacheSweep(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Daily, matching {@code searchAPI}'s own backstop. The interval is unit-bearing
     * ({@code 24h}, {@code PT12H}) — T871 is the reason that is not negotiable here.
     * {@code @SchedulerLock} keeps a cluster to one sweep rather than one per node.
     */
    @Scheduled(fixedRateString = "${turing.sn.snapshot.cache.ttl:24h}")
    @SchedulerLock(name = "snSnapshotCacheSweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void sweep() {
        SWEPT.forEach(this::sweepOne);
    }

    private void sweepOne(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        long entries = countEntries(cache);
        cache.clear();
        if (entries > 0) {
            log.info("[T905] Backstop sweep cleared {} entrie(s) from '{}'. This bounds how "
                    + "long a missed eviction can serve stale configuration; it does not mean "
                    + "any of them were stale.", entries, cacheName);
        }
    }

    /**
     * How many entries the sweep is about to discard, where the provider will say. Both
     * managers this app wires expose a {@link Map}; one that does not simply reports
     * nothing, because a count is the log line's only content and an invented one is worse
     * than none.
     */
    private static long countEntries(Cache cache) {
        Object nativeCache = cache.getNativeCache();
        return nativeCache instanceof Map<?, ?> map ? map.size() : 0L;
    }
}
