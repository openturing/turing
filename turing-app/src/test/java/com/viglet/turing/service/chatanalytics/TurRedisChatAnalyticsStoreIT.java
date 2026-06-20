/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

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
 * End-to-end coverage for {@link TurRedisChatAnalyticsStore} against a real
 * Redis (Testcontainers): upsert → query → aggregate → enrich → timeseries →
 * scorecard → tool-latency → purge. Drives the store's public API so the
 * Redis read/write/aggregation paths are exercised rather than mocked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurRedisChatAnalyticsStoreIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");

    private TurRedisChatAnalyticsStore store;

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
    void seed() {
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            jedis.flushAll();
        }
        String uri = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        store = new TurRedisChatAnalyticsStore(uri, "turtest");
        store.upsertSession(event("c1", "a1", TurChatSessionOutcome.COMPLETED, T0, 100, 5, 0));
        store.upsertSession(event("c2", "a1", TurChatSessionOutcome.ABANDONED, T0.plusSeconds(60), 40, 2, 1));
        store.upsertSession(event("c3", "a2", TurChatSessionOutcome.COMPLETED, T0.plusSeconds(120), 80, 3, 0));
    }

    private TurChatSessionEvent event(String conv, String agent, TurChatSessionOutcome outcome,
            Instant startedAt, long tokensOut, int toolCalls, int toolErrors) {
        return new TurChatSessionEvent(
                conv, agent, "persona-1", "llm-1", "embed-1", "store-1", "user-1", "en",
                "hello there", startedAt, startedAt.plusSeconds(30), outcome,
                4, 50, tokensOut, 30_000L, toolCalls, toolErrors, 250L,
                "exp-1", "control", null, "UTC", "desktop",
                List.of(new TurToolLatencySample("search", 120, true),
                        new TurToolLatencySample("fetch", 130, toolErrors == 0)));
    }

    private static Instant from() {
        return T0.minusSeconds(3600);
    }

    private static Instant to() {
        return T0.plusSeconds(3600);
    }

    private static TurChatSessionFilter noFilter() {
        return new TurChatSessionFilter(null, null, null, null, null, null);
    }

    @Test
    void engineAndEnabled() {
        assertThat(store.isEnabled()).isTrue();
        assertThat(store.getEngine()).isEqualTo(TurChatAnalyticsEngine.REDIS);
    }

    @Test
    void findSessionByIdReturnsStoredFields() {
        Map<String, Object> session = store.findSessionById("c1");
        assertThat(session).containsEntry("agentId", "a1");
        assertThat(session.get("outcome")).isEqualTo(TurChatSessionOutcome.COMPLETED.name());
        Map<String, Object> missing = store.findSessionById("missing");
        assertThat(missing == null || missing.isEmpty()).isTrue();
    }

    @Test
    void findRecentSessionsRespectsWindowAndFilter() {
        List<Map<String, Object>> all = store.findRecentSessions(from(), to(), noFilter(), 50);
        assertThat(all).hasSize(3);

        TurChatSessionFilter a1 = new TurChatSessionFilter("a1", null, null, null, null, null);
        List<Map<String, Object>> onlyA1 = store.findRecentSessions(from(), to(), a1, 50);
        assertThat(onlyA1).hasSize(2);
    }

    @Test
    void aggregateGroupsByDimension() {
        List<Map<String, Object>> byAgent = store.aggregate(from(), to(), "agentId", 10);
        assertThat(byAgent).isNotEmpty();
        assertThat(byAgent).allSatisfy(g -> assertThat(g).containsKeys("bucket", "count"));
        assertThat(byAgent).anySatisfy(g -> assertThat(g).containsEntry("bucket", "a1"));

        List<Map<String, Object>> byOutcome = store.aggregate(from(), to(), "outcome", 10);
        assertThat(byOutcome).isNotEmpty();
    }

    @Test
    void enrichmentFlowMovesSessionOutOfUnenriched() {
        List<Map<String, Object>> unenriched = store.findUnenrichedSessions(10);
        assertThat(unenriched).hasSize(3);

        store.enrichSession("c1", new TurChatSessionEnrichment(
                TurChatIntentLabel.SUPPORT, 0.91, "wanted a refund",
                TurChatGoalAchieved.YES, TurChatSentiment.POSITIVE,
                List.of("refund", "billing"),
                List.of(TurChatSentiment.NEUTRAL, TurChatSentiment.POSITIVE), T0.plusSeconds(300)));

        assertThat(store.findUnenrichedSessions(10)).hasSize(2);
        Map<String, Object> enriched = store.findSessionById("c1");
        assertThat(enriched).containsEntry("sentiment", TurChatSentiment.POSITIVE.name());
        assertThat(enriched).containsEntry("goalAchieved", TurChatGoalAchieved.YES.name());
    }

    @Test
    void timeseriesProducesBuckets() {
        assertThat(store.timeseries(from(), to(), "sessions", "hour")).isNotNull();
        assertThat(store.timeseries(from(), to(), "avg_tokens_out", "day")).isNotNull();
        // rate metric path (errors / calls)
        assertThat(store.timeseries(from(), to(), "tool_error_rate_pct", "hour")).isNotNull();
    }

    @Test
    void scorecardAndToolLatencyExecute() {
        assertThat(store.scorecard(from(), to(), "agentId", Map.of(), 10)).isNotNull();
        List<Map<String, Object>> latency = store.toolLatency(from(), to(), "a1", 10);
        assertThat(latency).isNotNull();
    }

    @Test
    void purgeOlderThanRemovesOldSessions() {
        long purged = store.purgeOlderThan(T0.plusSeconds(90));
        // c1 (T0) and c2 (T0+60s) are older than the threshold; c3 (T0+120s) stays.
        assertThat(purged).isEqualTo(2);
        assertThat(store.findRecentSessions(from(), to(), noFilter(), 50)).hasSize(1);
    }
}
