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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

import lombok.extern.slf4j.Slf4j;

/**
 * T70 / §VII.8.a — multi-armed bandit picker for A/B chat-flow experiments.
 * Replaces the fixed-weight {@code trafficWeight} hash with Thompson sampling
 * on the Beta-Bernoulli posterior derived from observed conversion data.
 *
 * <p>For each variant the service maintains a {@code Beta(successes + 1,
 * failures + 1)} posterior — the {@code +1} prior is the conventional
 * Laplace smoothing that lets an unsampled variant still get traffic.
 * On every assignment a sample is drawn from each variant's posterior and
 * the highest-sample variant wins; over time the picker converges to the
 * arm with the highest conversion rate while continuing to probe the
 * others. Significantly less operator vigilance than fixed-weight A/B
 * and provably regret-optimal in the Bayesian sense.
 *
 * <p>Data comes from {@link TurExperimentSignificanceService#collectVariantStats}
 * so the bandit and the significance test agree on what counts as a
 * success. The default success metric is {@code GOAL_ACHIEVED} — call
 * sites can override per-experiment when the operator picks a different
 * conversion (handoff, lead capture).
 *
 * <p>Implementation notes:
 * <ul>
 *   <li><b>No third-party math</b>: the Marsaglia-Tsang Gamma sampler is
 *       small enough to bake in here. Beta(α, β) ≡ X / (X + Y) where
 *       X ~ Gamma(α, 1) and Y ~ Gamma(β, 1), so a Beta sample is two
 *       Gamma samples + one division.</li>
 *   <li><b>Cold start safety</b>: when no analytics data is reachable
 *       (Mongo / Redis disabled, or the experiment is new), every variant
 *       posterior is Beta(1, 1) = Uniform(0, 1), so the picker falls
 *       back to uniform random assignment automatically.</li>
 *   <li><b>RNG seam</b>: the constructor accepts a {@link Random} so
 *       tests can pin determinism; production wires
 *       {@link ThreadLocalRandom#current()}.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAbBanditService {

    /** Default lookback window when no caller-provided one is given. */
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final TurExperimentSignificanceService significanceService;
    private final Random rng;

    @Autowired
    public TurAbBanditService(TurExperimentSignificanceService significanceService) {
        this(significanceService, null);
    }

    /** Test-only constructor — pass a seeded Random for deterministic picks. */
    public TurAbBanditService(TurExperimentSignificanceService significanceService, Random rng) {
        this.significanceService = significanceService;
        this.rng = rng; // when null, runtime uses ThreadLocalRandom
    }

    /**
     * Selects a variant via Thompson sampling on observed conversion data.
     * Returns the input {@code variants.get(0)} when the list has fewer
     * than 2 entries — the engine's caller will never invoke bandit
     * picking with a 1-arm experiment, but the defensive return keeps the
     * caller pattern uniform with {@code assignVariant}.
     */
    public TurChatFlow pickVariant(List<TurChatFlow> variants, String experimentKey) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("variants must be non-empty");
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }
        Map<String, long[]> statsByLabel = loadStats(experimentKey);
        Random r = rng != null ? rng : ThreadLocalRandom.current();
        TurChatFlow best = variants.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (TurChatFlow v : variants) {
            String label = v.getVariantLabel();
            long[] s = label == null ? null : statsByLabel.get(label);
            long sessions = s == null ? 0L : s[0];
            long successes = s == null ? 0L : s[1];
            long failures = Math.max(0L, sessions - successes);
            // Beta(alpha=successes+1, beta=failures+1) with Laplace prior.
            double sample = sampleBeta(r, successes + 1.0, failures + 1.0);
            if (sample > bestScore) {
                bestScore = sample;
                best = v;
            }
        }
        log.info("[Bandit] experimentKey='{}': picked variant '{}' (score={})",
                experimentKey, best.getVariantLabel(), bestScore);
        return best;
    }

    /**
     * Returns true when at least one variant has the per-flow
     * {@code banditEnabled} flag set. The engine reads this once per
     * assignment to decide whether to route through Thompson sampling.
     */
    public static boolean isBanditExperiment(List<TurChatFlow> variants) {
        if (variants == null) return false;
        for (TurChatFlow v : variants) {
            if (Boolean.TRUE.equals(v.getBanditEnabled())) return true;
        }
        return false;
    }

    private Map<String, long[]> loadStats(String experimentKey) {
        Map<String, long[]> out = new HashMap<>();
        try {
            Instant from = Instant.now().minusSeconds(DEFAULT_LOOKBACK_DAYS * 24L * 3600L);
            Instant to = Instant.now();
            List<TurExperimentSignificanceService.VariantStat> rows =
                    significanceService.collectVariantStats(experimentKey,
                            TurExperimentSignificanceService.SuccessMetric.GOAL_ACHIEVED,
                            from, to);
            for (TurExperimentSignificanceService.VariantStat row : rows) {
                out.put(row.variantLabel(),
                        new long[] { row.sessions(), row.successes() });
            }
        } catch (RuntimeException e) {
            // Cold start / analytics offline → fall back to Beta(1, 1)
            // per variant which is uniform — the picker keeps working
            // and self-corrects once data starts flowing.
            log.warn("[Bandit] failed to load stats for experimentKey='{}': {}",
                    experimentKey, e.getMessage());
        }
        return out;
    }

    /**
     * Beta sample via Beta(α, β) = X / (X + Y) where X ~ Gamma(α, 1),
     * Y ~ Gamma(β, 1).
     */
    static double sampleBeta(Random r, double alpha, double beta) {
        double x = sampleGamma(r, alpha);
        double y = sampleGamma(r, beta);
        return x / (x + y);
    }

    /**
     * Gamma(shape, 1) sample via Marsaglia & Tsang's acceptance-rejection
     * method. For shape &lt; 1 we use the Stuart trick:
     * Gamma(shape) ≡ Gamma(shape + 1) · U^(1/shape), with U ~ Uniform(0, 1).
     */
    static double sampleGamma(Random r, double shape) {
        if (shape < 1.0) {
            double u = r.nextDouble();
            double small = sampleGamma(r, shape + 1.0);
            // Clamp the exponent so u==0 doesn't blow up to infinity.
            return small * Math.pow(Math.max(u, 1e-300), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x;
            double v;
            do {
                x = r.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = r.nextDouble();
            double xSquared = x * x;
            if (u < 1.0 - 0.0331 * xSquared * xSquared) return d * v;
            if (Math.log(u) < 0.5 * xSquared + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }
}
