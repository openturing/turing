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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.format.annotation.DurationFormat.Unit;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.PropertyPlaceholderHelper;

import com.viglet.turing.genai.tool.TurSNSiteConfigCache;

/**
 * T905 / §LXVIII.12 — the backstop sweep clears both SN read-model caches, and its
 * interval says what unit it is in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSnapshotCacheSweepTest {

    private static final String SNAPSHOT = TurSNSiteSearchSnapshotService.CACHE_NAME;
    private static final String FIELD_CONFIG = TurSNSiteConfigCache.FIELD_CONFIG_CACHE;

    private static CacheManager seeded() {
        CacheManager cacheManager = new ConcurrentMapCacheManager(SNAPSHOT, FIELD_CONFIG);
        cacheManager.getCache(SNAPSHOT).put("site-a", "snapshot");
        cacheManager.getCache(FIELD_CONFIG).put("site-a", "fields");
        return cacheManager;
    }

    @Test
    void sweepClearsBothCaches() {
        CacheManager cacheManager = seeded();

        new TurSNSnapshotCacheSweep(cacheManager).sweep();

        assertNull(cacheManager.getCache(SNAPSHOT).get("site-a"),
                "the snapshot cache is the one with no other bound on staleness");
        assertNull(cacheManager.getCache(FIELD_CONFIG).get("site-a"),
                "the field-config cache projects the same graph and must go with it");
    }

    /** An empty cache is swept without incident, and without claiming it did anything. */
    @Test
    void sweepingAnEmptyCacheIsHarmless() {
        CacheManager cacheManager = new ConcurrentMapCacheManager(SNAPSHOT, FIELD_CONFIG);

        assertDoesNotThrow(() -> new TurSNSnapshotCacheSweep(cacheManager).sweep());
    }

    /** A manager that does not know these caches must not be forced to invent them. */
    @Test
    void sweepingAnUnknownCacheIsHarmless() {
        CacheManager empty = new ConcurrentMapCacheManager(new String[0]);

        assertDoesNotThrow(() -> new TurSNSnapshotCacheSweep(empty).sweep());
    }

    /** A provider whose native cache is not a Map still gets cleared; only the count is lost. */
    @Test
    void aNonMapNativeCacheIsStillCleared() {
        CacheManager cacheManager = new NoOpCacheManager();

        assertDoesNotThrow(() -> new TurSNSnapshotCacheSweep(cacheManager).sweep());
    }

    /**
     * T871 is why this is asserted rather than assumed: an interval whose unit is implied
     * is the defect that started this half of the block.
     */
    @Test
    void theSweepIntervalStatesItsUnit() throws NoSuchMethodException {
        String fixedRate = TurSNSnapshotCacheSweep.class.getMethod("sweep")
                .getAnnotation(Scheduled.class).fixedRateString();
        String resolved = new PropertyPlaceholderHelper("${", "}", ":", '\\', true)
                .replacePlaceholders(fixedRate, Map.<String, String>of()::get);

        assertEquals(Duration.ofHours(24),
                DurationFormatterUtils.detectAndParse(resolved, Unit.MILLIS),
                "the default must be a day, matching searchAPI's own backstop");
        assertTrue(fixedRate.contains("turing.sn.snapshot.cache.ttl"),
                "the interval must be configurable under a named property");
    }

    /** The two caches it sweeps are the two the snapshot eviction listener owns. */
    @Test
    void itSweepsExactlyTheCachesTheListenerEvicts() {
        CacheManager cacheManager = seeded();
        Cache snapshot = cacheManager.getCache(SNAPSHOT);
        assertNotNull(snapshot);

        new TurSNSnapshotCacheSweep(cacheManager).sweep();

        assertNull(snapshot.get("site-a"));
    }
}
