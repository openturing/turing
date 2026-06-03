/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.chatanalytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pools {@link TurToolLatencySample}s by tool name and produces the per-tool
 * percentile rows that back the T88 "which tool is the p95 outlier?" view.
 * Shared by the Mongo and Redis stores so both backends compute identical
 * percentiles regardless of where the raw samples live — the store's only job
 * is to collect the samples in the requested window; the math lives here.
 *
 * <p>Percentiles use the <b>nearest-rank</b> method on the ascending-sorted
 * latency list: {@code rank = ceil(p/100 * n)} (1-based), so p95 of 20 samples
 * is the 19th-smallest and p100 is the max. This is exact for the data we have
 * (we keep the actual samples, not a digest) and needs no interpolation —
 * cheap, deterministic, and trivially testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class TurToolLatencyAccumulator {

    private final Map<String, ToolBucket> byTool = new TreeMap<>();

    /** Folds one sample into its tool's bucket. */
    void add(TurToolLatencySample sample) {
        if (sample == null) return;
        byTool.computeIfAbsent(sample.tool(), k -> new ToolBucket()).add(sample);
    }

    /** Convenience for callers that hold the three primitive fields directly. */
    void add(String tool, long latencyMs, boolean success) {
        add(new TurToolLatencySample(tool, latencyMs, success));
    }

    boolean isEmpty() {
        return byTool.isEmpty();
    }

    /**
     * Materializes one row per tool, sorted by p95 descending (the outlier
     * floats to the top), capped at {@code maxTools}. Each row carries:
     * {@code tool}, {@code count}, {@code errors}, {@code errorRatePct}
     * (0-100), {@code avgMs}, {@code p50Ms}, {@code p95Ms}, {@code p99Ms},
     * {@code maxMs}, {@code minMs}.
     */
    List<Map<String, Object>> toRows(int maxTools) {
        int cap = Math.max(1, Math.min(maxTools, 200));
        List<Map<String, Object>> rows = new ArrayList<>(byTool.size());
        for (Map.Entry<String, ToolBucket> e : byTool.entrySet()) {
            ToolBucket b = e.getValue();
            if (b.latencies.isEmpty()) continue;
            b.latencies.sort(null); // ascending
            int n = b.latencies.size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool", e.getKey());
            row.put("count", b.count);
            row.put("errors", b.errors);
            row.put("errorRatePct", b.count > 0 ? b.errors * 100d / b.count : 0d);
            row.put("avgMs", b.sum / (double) n);
            row.put("minMs", b.latencies.get(0));
            row.put("p50Ms", percentile(b.latencies, 50));
            row.put("p95Ms", percentile(b.latencies, 95));
            row.put("p99Ms", percentile(b.latencies, 99));
            row.put("maxMs", b.latencies.get(n - 1));
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong(
                (Map<String, Object> r) -> ((Number) r.get("p95Ms")).longValue()).reversed());
        return rows.size() > cap ? new ArrayList<>(rows.subList(0, cap)) : rows;
    }

    /**
     * Nearest-rank percentile on an ascending-sorted list. {@code p} is in
     * {@code [0,100]}. Returns 0 for an empty list (defensive — callers skip
     * empty buckets). {@code rank = ceil(p/100 * n)} clamped to {@code [1, n]}.
     */
    static long percentile(List<Long> ascending, double p) {
        int n = ascending.size();
        if (n == 0) return 0L;
        if (n == 1) return ascending.get(0);
        int rank = (int) Math.ceil((p / 100d) * n);
        if (rank < 1) rank = 1;
        if (rank > n) rank = n;
        return ascending.get(rank - 1);
    }

    /** Mutable per-tool accumulator. */
    private static final class ToolBucket {
        final List<Long> latencies = new ArrayList<>();
        int count;
        int errors;
        long sum;

        void add(TurToolLatencySample s) {
            latencies.add(s.latencyMs());
            count++;
            if (!s.success()) errors++;
            sum += s.latencyMs();
        }
    }
}
