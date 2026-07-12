/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * T693 / §XLII.7 — per-backend transcription metrics. The T88 tool-latency
 * accumulator answers "which tool is the p95 outlier?"; this answers the same
 * for transcription backends: <b>which backend</b> (OPENAI vs a self-hosted
 * OPENAI_COMPATIBLE) costs how much latency, how many bytes, and how often it
 * fails — the data an operator needs to decide whether the local path is
 * fast/accurate enough or the cloud fallback is carrying the load.
 *
 * <p>Every {@link TurTranscriptionService#transcribe} call records one sample
 * keyed by the backend that served it (or the fallback backend when the
 * confidence fallback escalated). Samples are kept in a bounded in-process ring
 * per backend (newest wins, oldest evicted) — single-node, no persistence, the
 * same lightweight discipline as the SSE buses. {@link #rows()} pools them into
 * one percentile row per backend for the admin surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurTranscriptionMetricsService {

    /** Bounded ring depth per backend — enough for stable percentiles, capped memory. */
    static final int CAPACITY = 500;

    private final Map<TurTranscriptionProviderType, Deque<Sample>> byBackend =
            new EnumMap<>(TurTranscriptionProviderType.class);

    /** One observation of a transcription served by a backend. */
    record Sample(long latencyMs, boolean success, long audioBytes) {
        Sample {
            latencyMs = Math.max(0L, latencyMs);
            audioBytes = Math.max(0L, audioBytes);
        }
    }

    /**
     * Record one transcription. {@code backend} null is coerced to
     * {@link TurTranscriptionProviderType#NONE} so a metric is never dropped.
     */
    public synchronized void record(TurTranscriptionProviderType backend, long latencyMs,
            boolean success, long audioBytes) {
        TurTranscriptionProviderType key = backend == null ? TurTranscriptionProviderType.NONE : backend;
        Deque<Sample> ring = byBackend.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (ring.size() >= CAPACITY) {
            ring.pollFirst();
        }
        ring.addLast(new Sample(latencyMs, success, audioBytes));
    }

    /**
     * One row per backend, sorted by p95 latency descending: {@code backend},
     * {@code count}, {@code errors}, {@code errorRatePct}, {@code avgMs},
     * {@code p50Ms}, {@code p95Ms}, {@code maxMs}, {@code totalBytes}.
     */
    public synchronized List<Map<String, Object>> rows() {
        List<Map<String, Object>> rows = new ArrayList<>(byBackend.size());
        for (Map.Entry<TurTranscriptionProviderType, Deque<Sample>> e : byBackend.entrySet()) {
            List<Long> latencies = new ArrayList<>();
            int count = 0;
            int errors = 0;
            long sum = 0;
            long totalBytes = 0;
            for (Sample s : e.getValue()) {
                latencies.add(s.latencyMs());
                count++;
                if (!s.success()) {
                    errors++;
                }
                sum += s.latencyMs();
                totalBytes += s.audioBytes();
            }
            if (count == 0) {
                continue;
            }
            latencies.sort(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("backend", e.getKey().name());
            row.put("count", count);
            row.put("errors", errors);
            row.put("errorRatePct", errors * 100d / count);
            row.put("avgMs", sum / (double) count);
            row.put("p50Ms", percentile(latencies, 50));
            row.put("p95Ms", percentile(latencies, 95));
            row.put("maxMs", latencies.get(latencies.size() - 1));
            row.put("totalBytes", totalBytes);
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(
                ((Number) b.get("p95Ms")).longValue(), ((Number) a.get("p95Ms")).longValue()));
        return rows;
    }

    /** Nearest-rank percentile on an ascending-sorted list ({@code p} in [0,100]). */
    static long percentile(List<Long> ascending, double p) {
        int n = ascending.size();
        if (n == 0) {
            return 0L;
        }
        int rank = (int) Math.ceil((p / 100d) * n);
        return ascending.get(Math.clamp(rank, 1, n) - 1);
    }
}
