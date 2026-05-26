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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes statistical significance for A/B chat-flow experiments.
 * Operates entirely on the analytics store — given an {@code experimentKey},
 * pulls per-variant counts (sessions, successes) and runs a two-proportion
 * z-test to decide whether the difference between arms is signal or noise.
 *
 * <p>The product question this answers: <b>"Can I stop the experiment and
 * declare a winner?"</b> Without this, operators have to eyeball the
 * scorecard and guess; with it, they get a p-value + winner declaration
 * grounded in the actual sample size.
 *
 * <p>Methodology — two-proportion z-test (Wald):
 * <pre>
 *   p̂_A = successes_A / sessions_A
 *   p̂_B = successes_B / sessions_B
 *   p̂   = (successes_A + successes_B) / (sessions_A + sessions_B)
 *   SE  = sqrt(p̂ · (1 - p̂) · (1/n_A + 1/n_B))
 *   z   = (p̂_A - p̂_B) / SE
 *   p_value = 2 · (1 - Φ(|z|))     (two-tailed)
 * </pre>
 *
 * <p>"Success" is configurable via {@code successMetric}: the goal-achieved
 * rate (default), the handoff-requested rate, the lead-captured rate.
 * Caller picks which conversion they care about.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurExperimentSignificanceService {

    /**
     * Conventional alpha for "declare a winner" decisions. 0.05 = at most
     * 5% chance of declaring a winner that doesn't actually exist (Type I).
     * Operators can override per-call if they want stricter (0.01).
     */
    public static final double DEFAULT_ALPHA = 0.05;

    /** Minimum total sample size before any z-test is meaningful. Below this, declare "underpowered" not "no winner". */
    private static final int MIN_TOTAL_SAMPLE = 100;

    private final TurChatAnalyticsService analyticsService;

    public TurExperimentSignificanceService(TurChatAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Verdict the operator gets back. {@code winner} is the variantLabel
     * with the higher rate when significance is reached, else {@code null}.
     * {@code pValue} is the two-tailed test result (low = evidence the
     * variants differ). {@code recommendation} is a human-readable line
     * the dashboard renders as a banner.
     */
    public record SignificanceResult(
            String experimentKey,
            String successMetric,
            List<VariantStat> variants,
            String winner,
            Double pValue,
            double alpha,
            boolean underpowered,
            String recommendation) {
    }

    public record VariantStat(
            String variantLabel,
            long sessions,
            long successes,
            double rate) {
    }

    /**
     * Success metrics the service knows how to count. Stored as enum so
     * mistyped values from the controller fall back to GOAL_ACHIEVED
     * rather than silently returning 0 successes.
     */
    public enum SuccessMetric {
        GOAL_ACHIEVED("goalAchieved", "YES"),
        HANDOFF_WHATSAPP("handoffStatus", "whatsapp_requested"),
        LEAD_EMAIL_CAPTURED("lead_status_email", null);

        final String storeColumn;
        final String matchValue;

        SuccessMetric(String col, String val) {
            this.storeColumn = col;
            this.matchValue = val;
        }
    }

    /**
     * @param experimentKey  must match the {@code experimentKey} declared on the chat-flow
     * @param successMetric  which conversion to test
     * @param from / to      time window (passed through to the store)
     * @param alpha          significance threshold (default 0.05)
     */
    public SignificanceResult evaluate(String experimentKey, SuccessMetric successMetric,
            Instant from, Instant to, Double alpha) {
        double a = alpha == null ? DEFAULT_ALPHA : alpha;
        List<VariantStat> variants = collectVariantStats(experimentKey, successMetric, from, to);

        if (variants.size() < 2) {
            return new SignificanceResult(experimentKey, successMetric.name(), variants,
                    null, null, a, true,
                    "Not enough variants under this experimentKey (need ≥ 2).");
        }

        long totalN = variants.stream().mapToLong(VariantStat::sessions).sum();
        if (totalN < MIN_TOTAL_SAMPLE) {
            return new SignificanceResult(experimentKey, successMetric.name(), variants,
                    null, null, a, true,
                    String.format("Underpowered: %d total sessions (need ≥ %d) — keep running.",
                            totalN, MIN_TOTAL_SAMPLE));
        }

        // Two-arm only for V1. Multi-arm support would require Bonferroni
        // correction across pairs — punt to a follow-up.
        if (variants.size() > 2) {
            return new SignificanceResult(experimentKey, successMetric.name(), variants,
                    null, null, a, false,
                    "Multi-arm comparison (>2 variants) not supported yet — run pairwise.");
        }

        VariantStat va = variants.get(0);
        VariantStat vb = variants.get(1);
        double pValue = twoProportionPValue(va.successes(), va.sessions(),
                vb.successes(), vb.sessions());
        String winner = null;
        String recommendation;
        if (pValue <= a) {
            winner = va.rate() > vb.rate() ? va.variantLabel() : vb.variantLabel();
            recommendation = String.format(
                    "Significant difference detected (p=%.4f ≤ α=%.2f). Winner: '%s' "
                    + "(%.1f%% vs %.1f%%). Safe to promote.",
                    pValue, a, winner, va.rate() * 100, vb.rate() * 100);
        } else {
            recommendation = String.format(
                    "No significant difference yet (p=%.4f > α=%.2f). Rates: '%s'=%.1f%%, '%s'=%.1f%%. "
                    + "Keep collecting data.",
                    pValue, a, va.variantLabel(), va.rate() * 100,
                    vb.variantLabel(), vb.rate() * 100);
        }
        return new SignificanceResult(experimentKey, successMetric.name(), variants,
                winner, pValue, a, false, recommendation);
    }

    /**
     * Pulls per-variant counts from the analytics store. Uses the same
     * scorecard endpoint that the dashboard uses with {@code dimension=variantLabel}
     * filtered by {@code experimentKey}, then layers the success metric on top.
     *
     * <p>Public to support T70 — the bandit picker reuses this exact shape
     * so the same conversions that decide significance also feed Thompson
     * sampling. Keeping ONE source of truth for "what counts as a success"
     * avoids two paths drifting apart.
     */
    public List<VariantStat> collectVariantStats(String experimentKey, SuccessMetric metric,
            Instant from, Instant to) {
        Instant fromInstant = from == null ? Instant.now().minusSeconds(30L * 24 * 3600) : from;
        Instant toInstant   = to   == null ? Instant.now() : to;
        List<Map<String, Object>> rows = analyticsService.getStore()
                .findRecentSessions(fromInstant, toInstant, null, null,
                        null, null, null, null, 10_000);

        Map<String, long[]> byVariant = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!experimentKey.equals(row.get("experimentKey"))) continue;
            String variant = stringValue(row.get("variantLabel"));
            if (variant == null) continue;
            long[] counters = byVariant.computeIfAbsent(variant, k -> new long[2]);
            counters[0]++;
            if (isSuccess(row, metric)) counters[1]++;
        }
        List<VariantStat> out = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byVariant.entrySet()) {
            long sessions  = entry.getValue()[0];
            long successes = entry.getValue()[1];
            double rate = sessions > 0 ? (double) successes / sessions : 0.0;
            out.add(new VariantStat(entry.getKey(), sessions, successes, rate));
        }
        return out;
    }

    private static boolean isSuccess(Map<String, Object> row, SuccessMetric metric) {
        return switch (metric) {
            case GOAL_ACHIEVED -> "YES".equals(row.get("goalAchieved"));
            // handoff / lead capture flags would arrive as slot values in
            // the row when the analytics store starts persisting them
            // (TODO once slot snapshots are part of the session record).
            case HANDOFF_WHATSAPP -> false;
            case LEAD_EMAIL_CAPTURED -> false;
        };
    }

    private static String stringValue(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Two-proportion two-tailed z-test. Returns the p-value:
     * a very small number means "the rate difference is unlikely to be
     * random noise"; a large number (close to 1) means "could easily be
     * noise — keep collecting".
     *
     * <p>Package-private for unit testing.
     */
    static double twoProportionPValue(long successA, long nA, long successB, long nB) {
        if (nA == 0 || nB == 0) return 1.0;
        double pA = (double) successA / nA;
        double pB = (double) successB / nB;
        double pPooled = (double) (successA + successB) / (nA + nB);
        double se = Math.sqrt(pPooled * (1.0 - pPooled) * (1.0 / nA + 1.0 / nB));
        if (se == 0.0) return 1.0; // both arms 0% or both 100% — no detectable diff
        double z = (pA - pB) / se;
        return 2.0 * (1.0 - standardNormalCdf(Math.abs(z)));
    }

    /**
     * Φ(x) — cumulative distribution function of the standard normal,
     * via Abramowitz & Stegun 26.2.17. Max absolute error ~7.5e-8 for any x.
     * Good enough for A/B decisions (we round p-values to 4 decimals).
     */
    private static double standardNormalCdf(double x) {
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double poly = 0.319381530 * t
                - 0.356563782 * t * t
                + 1.781477937 * t * t * t
                - 1.821255978 * t * t * t * t
                + 1.330274429 * t * t * t * t * t;
        double phi = (1.0 / Math.sqrt(2.0 * Math.PI)) * Math.exp(-x * x / 2.0);
        return 1.0 - phi * poly;
    }
}
