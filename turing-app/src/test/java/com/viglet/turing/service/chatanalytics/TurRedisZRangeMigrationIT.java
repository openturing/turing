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

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZRangeParams;

/**
 * Pins the {@code zrangeByScore}/{@code zrevrangeByScore} → {@code zrange(ZRangeParams)}
 * translation used by {@link TurRedisChatAnalyticsStore}. The deprecated typed
 * range methods are replaced by the modern {@code ZRANGE ... BYSCORE [REV] [LIMIT]}
 * form (Jedis 5); this IT proves — against a real Redis — that the rewrite is
 * behavior-preserving for the exact three query shapes the store issues:
 *
 * <ol>
 *   <li>forward score range (no limit),</li>
 *   <li>reverse score range with a {@code LIMIT offset count} (note: with
 *       {@code REV} the start/stop are max/min, i.e. the same arg order the old
 *       {@code zrevrangeByScore(key, max, min, ...)} used),</li>
 *   <li>open/exclusive string bounds ({@code "-inf"}, {@code "(<score>"}).</li>
 * </ol>
 *
 * Asserting old == new is the strongest guarantee the migration changed nothing;
 * it also becomes the regression net this store previously lacked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurRedisZRangeMigrationIT {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private Jedis jedis;
    private static final String KEY = "z:byTime";

    @BeforeAll
    void setUp() {
        REDIS.start();
        jedis = new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379));
        // Five sessions at distinct epoch-milli scores.
        jedis.zadd(KEY, 1000, "a");
        jedis.zadd(KEY, 2000, "b");
        jedis.zadd(KEY, 3000, "c");
        jedis.zadd(KEY, 4000, "d");
        jedis.zadd(KEY, 5000, "e");
    }

    @AfterAll
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
        REDIS.stop();
    }

    @Test
    @SuppressWarnings("deprecation")
    void forwardRange_oldEqualsNew() {
        String from = "2000";
        String to = "4000";
        List<String> oldWay = jedis.zrangeByScore(KEY, from, to);
        List<String> newWay = jedis.zrange(KEY, new ZRangeParams(Protocol.Keyword.BYSCORE, from, to));
        assertThat(newWay).isEqualTo(oldWay).containsExactly("b", "c", "d");
    }

    @Test
    @SuppressWarnings("deprecation")
    void reverseRangeWithLimit_oldEqualsNew() {
        // Store pattern (line 185): zrevrangeByScore(key, max=to, min=from, 0, count).
        String to = "5000";
        String from = "1000";
        int count = 3;
        List<String> oldWay = jedis.zrevrangeByScore(KEY, to, from, 0, count);
        List<String> newWay = jedis.zrange(KEY,
                new ZRangeParams(Protocol.Keyword.BYSCORE, to, from).rev().limit(0, count));
        assertThat(newWay).isEqualTo(oldWay).containsExactly("e", "d", "c");
    }

    @Test
    @SuppressWarnings("deprecation")
    void openAndExclusiveBounds_oldEqualsNew() {
        // Store pattern (line 483): zrangeByScore(key, "-inf", "(threshold").
        String min = "-inf";
        String exclusiveMax = "(4000";
        List<String> oldWay = jedis.zrangeByScore(KEY, min, exclusiveMax);
        List<String> newWay = jedis.zrange(KEY, new ZRangeParams(Protocol.Keyword.BYSCORE, min, exclusiveMax));
        assertThat(newWay).isEqualTo(oldWay).containsExactly("a", "b", "c");
    }
}
