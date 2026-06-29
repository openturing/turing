/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.observability;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * T32 / §IV.7 — registers Micrometer {@code Gauge}s for every Hazelcast
 * {@code IMap} backing a Spring {@code @Cacheable} cache that the chat
 * pipeline reads. The gauges report:
 *
 * <ul>
 *   <li>{@link TurMeterNames#CHAT_CACHE_HITS} — cumulative hits since
 *       JVM start ({@link LocalMapStats#getHits()});</li>
 *   <li>{@link TurMeterNames#CHAT_CACHE_MISSES} — cumulative misses,
 *       derived as {@code getOperationCount - hits} since the Hazelcast
 *       5.x {@code LocalMapStats} interface exposes the read total but
 *       not a direct miss counter;</li>
 *   <li>{@link TurMeterNames#CHAT_CACHE_SIZE} — current local entry count
 *       ({@code IMap.size()}).</li>
 * </ul>
 *
 * <p>All three are tagged with {@link TurMeterNames#TAG_CACHE} so a
 * single Prometheus query can chart per-cache hit rate
 * ({@code hits / (hits + misses)}) or alert when a cache's
 * {@code size} crosses a threshold.
 *
 * <h2>Why a registrar instead of a per-cache @Cacheable hook</h2>
 *
 * Spring's {@code @Cacheable} doesn't surface hit/miss events to the
 * caller — only the cache provider does. With Hazelcast as the
 * {@code CacheManager}, every {@code @Cacheable} method backs onto an
 * {@code IMap}, and the {@code LocalMapStats} object is the
 * authoritative source. Registering gauges that pull from those stats
 * means we never write a Spring AOP wrapper around every cacheable
 * method (which would also add per-call overhead).
 *
 * <h2>Hazelcast-optional</h2>
 *
 * In dev mode (no clustered cache) Spring falls back to
 * {@code ConcurrentMapCacheManager} and there is no
 * {@link HazelcastInstance} bean. We use {@link ObjectProvider} so the
 * bean wires up cleanly when Hazelcast is present and silently no-ops
 * when it isn't (logged at INFO at startup so the absence is visible).
 *
 * <h2>Why fetch {@code IMap} lazily inside the gauge supplier</h2>
 *
 * {@link HazelcastInstance#getMap(String)} is idempotent — calling it
 * before the cache has been populated returns an empty map, which is
 * the correct "zero" state. Reading {@code getLocalMapStats()} on each
 * scrape (typically 15-30 s by Prometheus default) is cheap (in-memory
 * counters, no network round-trip).
 *
 * <h2>Cache catalog</h2>
 *
 * The registered set covers every cache the chat pipeline currently
 * reads — adding a new {@code @Cacheable} that the chat path consumes
 * means also adding its name to {@link #CHAT_CACHE_NAMES}. The list
 * sits alongside the corresponding {@code @CacheEvict} chains as a
 * checklist; T34 will eventually replace this manual roster with an
 * automated scan.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurChatCacheStatsRegistrar {

    /**
     * Hazelcast {@code IMap} names backing the chat pipeline's
     * {@code @Cacheable} surface. The order is documentation-friendly
     * (catalog reads first, T31 prompt-fragment caches next, T26/T28
     * Lucene-bucket caches last).
     */
    static final List<String> CHAT_CACHE_NAMES = List.of(
            // Per-instance analytics intent MLT index.
            "turAnalyticsIntentMLTIndex",
            // Router decision cache — T26 LLM-route memoization keyed
            // by (agentId, hash(userMessage)).
            "turChatFlowRouterDecision",
            // T31 prompt fragment caches (added 2026.3.1).
            "turPersonaStaticPrompt",
            "turChatFlowStaticAddendumHead",
            "turChatFlowStaticAddendumTail");

    private final MeterRegistry meterRegistry;
    private final ObjectProvider<HazelcastInstance> hazelcastInstanceProvider;

    public TurChatCacheStatsRegistrar(MeterRegistry meterRegistry,
            ObjectProvider<HazelcastInstance> hazelcastInstanceProvider) {
        this.meterRegistry = meterRegistry;
        this.hazelcastInstanceProvider = hazelcastInstanceProvider;
    }

    @PostConstruct
    void registerGauges() {
        HazelcastInstance hz = hazelcastInstanceProvider.getIfAvailable();
        if (hz == null) {
            // Dev mode / unit tests run without the clustered cache —
            // gauges would read zero forever, so skip the registration
            // entirely. The chat pipeline still works (Spring falls
            // back to ConcurrentMapCacheManager) and dashboards just
            // see the gauge series missing rather than a flat-zero
            // line, which is the honest signal.
            log.info("[CacheStats] HazelcastInstance bean not present — cache hit/miss gauges disabled");
            return;
        }
        for (String cacheName : CHAT_CACHE_NAMES) {
            registerCacheGauges(hz, cacheName);
        }
        log.info("[CacheStats] Registered Hazelcast cache gauges for {} caches", CHAT_CACHE_NAMES.size());
    }

    private void registerCacheGauges(HazelcastInstance hz, String cacheName) {
        Tags tags = Tags.of(TurMeterNames.TAG_CACHE, cacheName);
        Gauge.builder(TurMeterNames.CHAT_CACHE_HITS, () -> readStat(hz, cacheName, LocalMapStats::getHits))
                .tags(tags)
                .baseUnit("hits")
                .description("Cumulative Hazelcast IMap hit count since JVM start")
                .strongReference(true)
                .register(meterRegistry);
        // Hazelcast 5.x exposes `getOperationCount` (total reads) and
        // `getHits` (reads that found a value) but not a direct
        // {@code getMisses}. The misses gauge is therefore the
        // derived difference. Same shape Spring's {@code CacheManager}
        // bridge uses to count cache misses → bean-method execution.
        Gauge.builder(TurMeterNames.CHAT_CACHE_MISSES, () -> readMisses(hz, cacheName))
                .tags(tags)
                .baseUnit("misses")
                .description("Cumulative Hazelcast IMap miss count since JVM start (getOperationCount − hits)")
                .strongReference(true)
                .register(meterRegistry);
        Gauge.builder(TurMeterNames.CHAT_CACHE_SIZE, () -> readSize(hz, cacheName))
                .tags(tags)
                .baseUnit("entries")
                .description("Current Hazelcast IMap entry count on this node")
                .strongReference(true)
                .register(meterRegistry);
    }

    /**
     * Reads a single stat from the cache's {@link LocalMapStats}, swallowing
     * any failure mode (cluster unavailable, map not yet created) to a 0.0
     * — the gauge MUST NOT throw on scrape, or Prometheus will drop the
     * whole endpoint's scrape (not just this metric).
     */
    private static double readStat(HazelcastInstance hz, String cacheName,
            java.util.function.ToLongFunction<LocalMapStats> extractor) {
        try {
            IMap<Object, Object> map = hz.getMap(cacheName);
            return extractor.applyAsLong(map.getLocalMapStats());
        } catch (Exception e) {
            log.debug("[CacheStats] Could not read stats for '{}': {}", cacheName, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Derives the miss count from {@code getOperationCount} (total reads)
     * minus {@code getHits} (reads that found a value). Clamped at zero
     * because a stat reset between the two reads (extremely rare but
     * possible) could otherwise surface a negative value to Prometheus.
     */
    private static double readMisses(HazelcastInstance hz, String cacheName) {
        try {
            LocalMapStats stats = hz.getMap(cacheName).getLocalMapStats();
            long misses = stats.getGetOperationCount() - stats.getHits();
            return misses < 0 ? 0.0 : misses;
        } catch (Exception e) {
            log.debug("[CacheStats] Could not read miss count for '{}': {}", cacheName, e.getMessage());
            return 0.0;
        }
    }

    private static double readSize(HazelcastInstance hz, String cacheName) {
        try {
            return hz.getMap(cacheName).size();
        } catch (Exception e) {
            log.debug("[CacheStats] Could not read size for '{}': {}", cacheName, e.getMessage());
            return 0.0;
        }
    }
}
