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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pins the T88 per-tool latency percentile math shared by the Mongo and Redis
 * stores. Pure in-process arithmetic — no Testcontainers — so the percentile
 * contract is locked independently of where the raw samples are persisted.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurToolLatencyAccumulatorTest {

    @Test
    void percentile_nearestRank_picksExpectedSamples() {
        // 1..10 ascending. Nearest-rank: rank = ceil(p/100 * n).
        List<Long> asc = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(TurToolLatencyAccumulator.percentile(asc, 50)).isEqualTo(5L);  // ceil(5.0)=5 → idx 4
        assertThat(TurToolLatencyAccumulator.percentile(asc, 95)).isEqualTo(10L); // ceil(9.5)=10 → idx 9
        assertThat(TurToolLatencyAccumulator.percentile(asc, 99)).isEqualTo(10L);
        assertThat(TurToolLatencyAccumulator.percentile(asc, 100)).isEqualTo(10L);
        assertThat(TurToolLatencyAccumulator.percentile(asc, 0)).isEqualTo(1L);   // clamps rank to 1
    }

    @Test
    void percentile_singleAndEmpty() {
        assertThat(TurToolLatencyAccumulator.percentile(List.of(), 95)).isZero();
        assertThat(TurToolLatencyAccumulator.percentile(List.of(42L), 95)).isEqualTo(42L);
    }

    @Test
    void toRows_emptyAccumulator_returnsEmpty() {
        assertThat(new TurToolLatencyAccumulator().toRows(50)).isEmpty();
    }

    @Test
    void toRows_groupsByToolAndComputesStats() {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        // search_site: 4 calls, one failure, latencies 10/20/30/40.
        acc.add("search_site", 10, true);
        acc.add("search_site", 20, true);
        acc.add("search_site", 30, false);
        acc.add("search_site", 40, true);

        List<Map<String, Object>> rows = acc.toRows(50);
        assertThat(rows).hasSize(1);
        Map<String, Object> r = rows.get(0);
        assertThat(r).containsEntry("tool", "search_site");
        assertThat(r).containsEntry("count", 4);
        assertThat(r).containsEntry("errors", 1);
        assertThat(((Number) r.get("errorRatePct")).doubleValue()).isCloseTo(25.0, within(1e-9));
        assertThat(((Number) r.get("avgMs")).doubleValue()).isCloseTo(25.0, within(1e-9));
        assertThat(((Number) r.get("minMs")).longValue()).isEqualTo(10L);
        assertThat(((Number) r.get("maxMs")).longValue()).isEqualTo(40L);
        // p95 of [10,20,30,40] → rank ceil(3.8)=4 → 40.
        assertThat(((Number) r.get("p95Ms")).longValue()).isEqualTo(40L);
        // p50 → rank ceil(2.0)=2 → 20.
        assertThat(((Number) r.get("p50Ms")).longValue()).isEqualTo(20L);
    }

    @Test
    void toRows_sortsByP95Descending_outlierFirst() {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        // fast_tool: all 5ms (p95 = 5). slow_tool: all ~1000ms (p95 = 1000),
        // so it must float to the top as the p95 outlier.
        for (int i = 0; i < 20; i++) acc.add("fast_tool", 5, true);
        for (int i = 0; i < 20; i++) acc.add("slow_tool", 1000, true);

        List<Map<String, Object>> rows = acc.toRows(50);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("tool", "slow_tool");
        assertThat(((Number) rows.get(0).get("p95Ms")).longValue()).isEqualTo(1000L);
        assertThat(rows.get(1)).containsEntry("tool", "fast_tool");
        assertThat(((Number) rows.get(1).get("p95Ms")).longValue()).isEqualTo(5L);
    }

    @Test
    void toRows_capsAtMaxTools_keepingTopP95() {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        // tool with index i gets latency i*100, so higher index = higher p95.
        for (int i = 1; i <= 5; i++) acc.add("tool_" + i, i * 100L, true);

        List<Map<String, Object>> rows = acc.toRows(2);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("tool", "tool_5"); // 500ms
        assertThat(rows.get(1)).containsEntry("tool", "tool_4"); // 400ms
    }

    @Test
    void add_nullSampleIgnored_blankToolBecomesUnknown_negativeClamped() {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        acc.add((TurToolLatencySample) null);
        acc.add("  ", -5, true); // blank name → "unknown", negative latency → 0

        List<Map<String, Object>> rows = acc.toRows(50);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("tool", "unknown");
        assertThat(((Number) rows.get(0).get("minMs")).longValue()).isZero();
    }

    @Test
    void toRows_errorRateZeroWhenAllSucceed() {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        acc.add("ok_tool", 100, true);
        acc.add("ok_tool", 200, true);
        Map<String, Object> r = acc.toRows(50).get(0);
        assertThat(((Number) r.get("errorRatePct")).doubleValue()).isZero();
    }
}
