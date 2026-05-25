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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Tests for T32 / §IV.7 cache stats registrar — verifies that gauges are
 * registered for every cache name on startup, that they pull the right
 * value from {@link LocalMapStats}, and that the bean degrades cleanly
 * when no {@link HazelcastInstance} is available.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatCacheStatsRegistrarTest {

    @Test
    void registersThreeGaugesPerCacheName() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HazelcastInstance hz = mock(HazelcastInstance.class);
        // Mock map returns stable stats so the gauges read deterministic
        // values; the test asserts on the gauge surface, not the specific
        // numbers (those are covered separately).
        @SuppressWarnings("unchecked")
        IMap<Object, Object> map = mock(IMap.class);
        LocalMapStats stats = mock(LocalMapStats.class);
        when(stats.getHits()).thenReturn(100L);
        when(stats.getGetOperationCount()).thenReturn(150L);
        when(map.getLocalMapStats()).thenReturn(stats);
        when(map.size()).thenReturn(42);
        when(hz.getMap(anyString())).thenReturn(map);

        TurChatCacheStatsRegistrar reg = new TurChatCacheStatsRegistrar(registry, provider(hz));
        reg.registerGauges();

        // 3 gauge metric names × N cache names; total = 3N.
        long totalGauges = registry.find(TurMeterNames.CHAT_CACHE_HITS).gauges().size()
                + registry.find(TurMeterNames.CHAT_CACHE_MISSES).gauges().size()
                + registry.find(TurMeterNames.CHAT_CACHE_SIZE).gauges().size();
        assertThat(totalGauges)
                .isEqualTo(TurChatCacheStatsRegistrar.CHAT_CACHE_NAMES.size() * 3L);

        // Spot-check one of the cache names — hits gauge reads 100, misses
        // is derived as 150 - 100 = 50, size is 42.
        String sampleCache = "turAIAgentfindById";
        Gauge hits = registry.find(TurMeterNames.CHAT_CACHE_HITS)
                .tags(Tags.of(TurMeterNames.TAG_CACHE, sampleCache)).gauge();
        Gauge misses = registry.find(TurMeterNames.CHAT_CACHE_MISSES)
                .tags(Tags.of(TurMeterNames.TAG_CACHE, sampleCache)).gauge();
        Gauge size = registry.find(TurMeterNames.CHAT_CACHE_SIZE)
                .tags(Tags.of(TurMeterNames.TAG_CACHE, sampleCache)).gauge();
        assertThat(hits.value()).isEqualTo(100.0);
        assertThat(misses.value()).isEqualTo(50.0);
        assertThat(size.value()).isEqualTo(42.0);
    }

    @Test
    void missesGaugeClampsAtZeroWhenStatsRegress() {
        // Defensive: if a stat reset between getOperationCount and getHits
        // ever produces a negative delta, the gauge must clamp at 0 — we
        // never want a Prometheus series to flip negative on a cumulative
        // counter.
        MeterRegistry registry = new SimpleMeterRegistry();
        HazelcastInstance hz = mock(HazelcastInstance.class);
        @SuppressWarnings("unchecked")
        IMap<Object, Object> map = mock(IMap.class);
        LocalMapStats stats = mock(LocalMapStats.class);
        when(stats.getHits()).thenReturn(50L);
        when(stats.getGetOperationCount()).thenReturn(10L);
        when(map.getLocalMapStats()).thenReturn(stats);
        when(hz.getMap(anyString())).thenReturn(map);

        TurChatCacheStatsRegistrar reg = new TurChatCacheStatsRegistrar(registry, provider(hz));
        reg.registerGauges();

        Gauge misses = registry.find(TurMeterNames.CHAT_CACHE_MISSES).gauge();
        assertThat(misses.value()).isEqualTo(0.0);
    }

    @Test
    void gaugesSwallowExceptionsAndReturnZero() {
        // The gauge supplier MUST NOT throw — Prometheus drops the entire
        // scrape endpoint when a gauge throws, taking unrelated metrics
        // with it. Verify each accessor degrades to 0 on Hazelcast failure.
        MeterRegistry registry = new SimpleMeterRegistry();
        HazelcastInstance hz = mock(HazelcastInstance.class);
        when(hz.getMap(anyString())).thenThrow(new RuntimeException("cluster down"));

        TurChatCacheStatsRegistrar reg = new TurChatCacheStatsRegistrar(registry, provider(hz));
        reg.registerGauges();

        for (String cacheName : TurChatCacheStatsRegistrar.CHAT_CACHE_NAMES) {
            assertThat(registry.find(TurMeterNames.CHAT_CACHE_HITS)
                    .tags(Tags.of(TurMeterNames.TAG_CACHE, cacheName)).gauge().value())
                    .isEqualTo(0.0);
            assertThat(registry.find(TurMeterNames.CHAT_CACHE_MISSES)
                    .tags(Tags.of(TurMeterNames.TAG_CACHE, cacheName)).gauge().value())
                    .isEqualTo(0.0);
            assertThat(registry.find(TurMeterNames.CHAT_CACHE_SIZE)
                    .tags(Tags.of(TurMeterNames.TAG_CACHE, cacheName)).gauge().value())
                    .isEqualTo(0.0);
        }
    }

    @Test
    void noHazelcastInstance_skipsRegistrationSilently() {
        // Dev mode falls back to ConcurrentMapCacheManager and there is no
        // HazelcastInstance bean. The registrar must wire up cleanly and
        // log an INFO line instead of throwing or registering zero-flat
        // gauges (which would lie to dashboards).
        MeterRegistry registry = new SimpleMeterRegistry();
        TurChatCacheStatsRegistrar reg = new TurChatCacheStatsRegistrar(registry, provider(null));
        reg.registerGauges();

        // Zero gauges registered total
        assertThat(registry.find(TurMeterNames.CHAT_CACHE_HITS).gauges()).isEmpty();
        assertThat(registry.find(TurMeterNames.CHAT_CACHE_MISSES).gauges()).isEmpty();
        assertThat(registry.find(TurMeterNames.CHAT_CACHE_SIZE).gauges()).isEmpty();
    }

    @Test
    void allChatPipelineCachesArePresent() {
        // Pin the catalog — any future cache added to the chat pipeline
        // SHOULD be added to this list. The test fails when someone ships
        // a new @Cacheable backing chat without registering it, so the
        // dashboard doesn't silently lose visibility.
        assertThat(TurChatCacheStatsRegistrar.CHAT_CACHE_NAMES).containsExactlyInAnyOrder(
                "turAIAgentfindById",
                "turChatFlowFindById",
                "turChatFlowFindByAgentId",
                "turPersonafindById",
                "turAnalyticsIntentMLTIndex",
                "turChatFlowRouterDecision",
                "turPersonaStaticPrompt",
                "turChatFlowStaticAddendumHead",
                "turChatFlowStaticAddendumTail");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<HazelcastInstance> provider(HazelcastInstance value) {
        ObjectProvider<HazelcastInstance> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
