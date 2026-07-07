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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

/**
 * Pins the T70 bandit picker: Thompson sampling on Beta-Bernoulli
 * posteriors with Laplace prior; cold-start fallback to uniform; +
 * the {@code isBanditExperiment} short-circuit that lets the engine
 * skip bandit math for plain weighted-hash experiments.
 */
@ExtendWith(MockitoExtension.class)
class TurAbBanditServiceTest {

    @Mock
    private TurExperimentSignificanceService significanceService;

    private TurAbBanditService bandit(long seed) {
        return new TurAbBanditService(significanceService, new Random(seed));
    }

    private TurChatFlow variant(String label, boolean banditEnabled) {
        TurChatFlow f = new TurChatFlow();
        f.setId(label);
        f.setVariantLabel(label);
        f.setBanditEnabled(banditEnabled);
        return f;
    }

    @Test
    void pickVariantRequiresNonEmpty() {
        var bandit = bandit(0);
        List<TurChatFlow> empty = List.of();
        assertThatThrownBy(() -> bandit.pickVariant(empty, "exp"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bandit.pickVariant(null, "exp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pickVariantReturnsLoneVariantWithoutSampling() {
        TurChatFlow only = variant("A", true);
        assertThat(bandit(0).pickVariant(List.of(only), "exp")).isSameAs(only);
        // No analytics call needed.
    }

    @Test
    void pickVariantConvergesToHigherConversionRate() {
        // A: 100 sessions, 80 successes (80% conversion)
        // B: 100 sessions, 20 successes (20% conversion)
        // Thompson sampling should pick A in the overwhelming majority of
        // runs once the posteriors are tight.
        when(significanceService.collectVariantStats(eq("exp"), any(), any(), any()))
                .thenReturn(List.of(
                        new TurExperimentSignificanceService.VariantStat("A", 100, 80, 0.8),
                        new TurExperimentSignificanceService.VariantStat("B", 100, 20, 0.2)));
        List<TurChatFlow> variants = List.of(variant("A", true), variant("B", true));
        int aPicks = 0;
        // Use deterministic Random so the assertion isn't flaky.
        TurAbBanditService svc = bandit(42L);
        for (int i = 0; i < 200; i++) {
            if ("A".equals(svc.pickVariant(variants, "exp").getId())) aPicks++;
        }
        // With this much separation (z >> 5σ), >95% of picks should land on A.
        assertThat(aPicks).isGreaterThan(190);
    }

    @Test
    void pickVariantBalancesUnderColdStart() {
        // No analytics data → both variants get Beta(1, 1) = Uniform(0, 1).
        // Distribution must be roughly 50/50 over many trials.
        when(significanceService.collectVariantStats(eq("cold"), any(), any(), any()))
                .thenReturn(List.of());
        List<TurChatFlow> variants = List.of(variant("A", true), variant("B", true));
        TurAbBanditService svc = bandit(7L);
        int aPicks = 0;
        for (int i = 0; i < 1000; i++) {
            if ("A".equals(svc.pickVariant(variants, "cold").getId())) aPicks++;
        }
        // 95% binomial interval around 500 is roughly [438, 562]; widen slightly.
        assertThat(aPicks).isBetween(420, 580);
    }

    @Test
    void pickVariantFallsBackOnAnalyticsFailure() {
        when(significanceService.collectVariantStats(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Mongo down"));
        List<TurChatFlow> variants = List.of(variant("A", true), variant("B", true));
        // Must not throw — degrades to uniform-prior sampling.
        TurChatFlow picked = bandit(99L).pickVariant(variants, "exp");
        assertThat(picked).isIn(variants);
    }

    @Test
    void isBanditExperimentDetectsAnyBanditFlag() {
        assertThat(TurAbBanditService.isBanditExperiment(
                List.of(variant("A", false), variant("B", false))))
                .isFalse();
        assertThat(TurAbBanditService.isBanditExperiment(
                List.of(variant("A", false), variant("B", true))))
                .isTrue();
        assertThat(TurAbBanditService.isBanditExperiment(null)).isFalse();
        assertThat(TurAbBanditService.isBanditExperiment(List.of())).isFalse();
    }

    @Test
    void isBanditExperimentTreatsNullAsFalse() {
        TurChatFlow flow = new TurChatFlow();
        flow.setBanditEnabled(null);
        assertThat(TurAbBanditService.isBanditExperiment(List.of(flow))).isFalse();
    }

    @Test
    void sampleBetaIsBoundedInUnitInterval() {
        Random r = new Random(0);
        for (int i = 0; i < 5_000; i++) {
            double s = TurAbBanditService.sampleBeta(r, 1.0 + r.nextInt(100), 1.0 + r.nextInt(100));
            assertThat(s).isBetween(0.0, 1.0);
        }
    }

    @Test
    void sampleBetaMeanApproximatesAlphaOverAlphaPlusBeta() {
        Random r = new Random(123);
        double alpha = 5.0;
        double beta = 15.0;
        double sum = 0.0;
        int trials = 20_000;
        for (int i = 0; i < trials; i++) {
            sum += TurAbBanditService.sampleBeta(r, alpha, beta);
        }
        double mean = sum / trials;
        double expected = alpha / (alpha + beta); // 0.25
        // Sample mean tolerance around 1.5% on 20k samples.
        assertThat(mean).isBetween(expected - 0.015, expected + 0.015);
    }

    @Test
    void sampleGammaIsPositive() {
        Random r = new Random(1);
        for (double shape : new double[] {0.1, 0.5, 1.0, 2.0, 5.0, 100.0}) {
            for (int i = 0; i < 200; i++) {
                double g = TurAbBanditService.sampleGamma(r, shape);
                assertThat(g).isPositive();
            }
        }
    }

    // ─────────────────────────── T241 — per-experiment success metric ───────────────────────────

    @Test
    void resolveSuccessMetricDefaultsToGoalAchievedWhenNoneDeclared() {
        List<TurChatFlow> variants = List.of(variant("A", true), variant("B", true));
        assertThat(TurAbBanditService.resolveSuccessMetric(variants))
                .isEqualTo(TurExperimentSignificanceService.SuccessMetric.GOAL_ACHIEVED);
    }

    @Test
    void resolveSuccessMetricReadsFirstDeclaredArm() {
        TurChatFlow a = variant("A", true); // no metric
        TurChatFlow b = variant("B", true);
        b.setExperimentSuccessMetric("HANDOFF_WHATSAPP");
        assertThat(TurAbBanditService.resolveSuccessMetric(List.of(a, b)))
                .isEqualTo(TurExperimentSignificanceService.SuccessMetric.HANDOFF_WHATSAPP);
    }

    @Test
    void resolveSuccessMetricFallsBackToDefaultOnUnknownName() {
        TurChatFlow a = variant("A", true);
        a.setExperimentSuccessMetric("NOT_A_REAL_METRIC");
        assertThat(TurAbBanditService.resolveSuccessMetric(List.of(a)))
                .isEqualTo(TurExperimentSignificanceService.SuccessMetric.GOAL_ACHIEVED);
    }

    @Test
    void pickVariantScoresOnTheDeclaredExperimentMetric() {
        // The declared metric must be the one the bandit asks the analytics
        // store for — otherwise the experiment optimises the wrong conversion.
        when(significanceService.collectVariantStats(eq("exp"),
                eq(TurExperimentSignificanceService.SuccessMetric.LEAD_EMAIL_CAPTURED),
                any(), any()))
                .thenReturn(List.of(
                        new TurExperimentSignificanceService.VariantStat("A", 100, 90, 0.9),
                        new TurExperimentSignificanceService.VariantStat("B", 100, 10, 0.1)));
        TurChatFlow a = variant("A", true);
        a.setExperimentSuccessMetric("LEAD_EMAIL_CAPTURED");
        TurChatFlow b = variant("B", true);
        // Picks must converge to A using the LEAD metric stats — proving the
        // stub keyed on LEAD_EMAIL_CAPTURED is the one consulted.
        TurAbBanditService svc = bandit(42L);
        int aPicks = 0;
        for (int i = 0; i < 200; i++) {
            if ("A".equals(svc.pickVariant(List.of(a, b), "exp").getId())) aPicks++;
        }
        assertThat(aPicks).isGreaterThan(190);
    }

    @Test
    void pickVariantHandlesMissingStatsForOneVariant() {
        // C has stats; D has none → D gets Beta(1, 1) prior.
        when(significanceService.collectVariantStats(eq("partial"), any(), any(), any()))
                .thenReturn(List.of(
                        new TurExperimentSignificanceService.VariantStat("C", 50, 25, 0.5)));
        Map<String, Integer> picks = new HashMap<>();
        TurAbBanditService svc = bandit(11L);
        List<TurChatFlow> variants = List.of(variant("C", true), variant("D", true));
        for (int i = 0; i < 200; i++) {
            String id = svc.pickVariant(variants, "partial").getId();
            picks.merge(id, 1, Integer::sum);
        }
        // Both should be picked at least once — D's uniform prior must not
        // be silently dropped.
        assertThat(picks).containsKeys("C", "D");
    }
}
