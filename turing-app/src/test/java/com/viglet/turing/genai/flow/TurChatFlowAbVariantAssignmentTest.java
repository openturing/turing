/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

/**
 * Unit tests for the deterministic A/B variant assignment helper used by
 * {@link TurChatFlowEngineService#assignVariant}. Pure-function tests —
 * no Spring context, no DB. Three concerns covered:
 *
 * <ol>
 *   <li><b>Stickiness</b>: same {@code (conversationId, experimentKey)}
 *       maps to the same variant on every call.</li>
 *   <li><b>Weighting</b>: distribution across a large sample of random
 *       conversation ids honors the declared {@code trafficWeight} ratio
 *       within tolerance.</li>
 *   <li><b>Edge cases</b>: zero-weight exclusion, null fallback to
 *       uniform, single-variant identity.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurChatFlowAbVariantAssignmentTest {

    // Fixed reference instant — the window-bound tests set starts/ends relative
    // to it, so a literal keeps the eligibility checks deterministic.
    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static final String KEY = "programa-match-headline";

    // ─────────────────────────── Stickiness ───────────────────────────

    @Test
    void assignVariant_sameConversationAlwaysGetsSameVariant() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "treatment", 50));
        String conv = "conv-12345";
        TurChatFlow first = TurChatFlowEngineService.assignVariant(variants, conv, KEY);
        for (int i = 0; i < 50; i++) {
            assertThat(TurChatFlowEngineService.assignVariant(variants, conv, KEY))
                    .as("Call %d must return the same variant — assignment is sticky", i)
                    .isSameAs(first);
        }
    }

    @Test
    void assignVariant_differentExperimentsBucketIndependently() {
        // Same conversation hitting two different experiments should NOT
        // land on the same arm by simply replaying the conv hash — the
        // experimentKey must mix into the hash. Using 4 variants makes
        // the test robust: P(same arm by chance on a single conv) = 1/4,
        // and across 200 convs P(all match) = (1/4)^200 ≈ 0.
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 1),
                variant("B", "v1", 1),
                variant("C", "v2", 1),
                variant("D", "v3", 1));
        int divergedConvs = 0;
        for (int i = 0; i < 200; i++) {
            String conv = "conv-" + i;
            TurChatFlow expA = TurChatFlowEngineService.assignVariant(variants, conv, "exp-headline");
            TurChatFlow expB = TurChatFlowEngineService.assignVariant(variants, conv, "exp-cta-color");
            if (expA != expB) divergedConvs++;
        }
        // With 4 arms each conv has ~75% chance of bucketing differently;
        // expect roughly 150/200 but allow generous slack.
        assertThat(divergedConvs)
                .as("experimentKey must be part of the hash — without it the salt is just convId")
                .isGreaterThan(50);
    }

    // ─────────────────────────── Weighting ───────────────────────────

    @Test
    void assignVariant_50_50_splitsTrafficEvenly() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "treatment", 50));
        Map<String, Integer> counts = simulate(variants, 5_000);
        // Allow ±5% drift — hash distribution on synthetic UUIDs is
        // close to uniform but not perfect.
        assertThat(counts.get("A")).isBetween(2_250, 2_750);
        assertThat(counts.get("B")).isBetween(2_250, 2_750);
    }

    @Test
    void assignVariant_90_10_splitFavorsHeavyArm() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 90),
                variant("B", "treatment", 10));
        Map<String, Integer> counts = simulate(variants, 5_000);
        // ±2.5% tolerance for the 10% arm; the 90% arm gets ±5%.
        assertThat(counts.get("A")).isBetween(4_350, 4_650);
        assertThat(counts.get("B")).isBetween(350, 650);
    }

    @Test
    void assignVariant_ratiosNormalize_so_1_1_equals_50_50() {
        // Weights 1:1 and 50:50 represent the same RATIO — at scale both
        // should produce a 50/50 distribution. They do NOT produce the
        // same per-conv assignment, though, because the modulus differs
        // (floorMod(hash, 2) vs floorMod(hash, 100)). The DISTRIBUTION
        // equivalence is what matters — both should split traffic evenly.
        List<TurChatFlow> small = List.of(
                variant("A", "control", 1),
                variant("B", "treatment", 1));
        List<TurChatFlow> big = List.of(
                variant("A", "control", 50),
                variant("B", "treatment", 50));
        Map<String, Integer> smallCounts = simulate(small, 5_000);
        Map<String, Integer> bigCounts = simulate(big, 5_000);
        // Both must produce ~50/50 within tolerance.
        assertThat(smallCounts.get("A")).isBetween(2_250, 2_750);
        assertThat(bigCounts.get("A")).isBetween(2_250, 2_750);
    }

    // ─────────────────────────── Edge cases ───────────────────────────

    @Test
    void assignVariant_zeroWeightVariantIsExcluded() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "paused", 0),
                variant("C", "treatment", 50));
        Map<String, Integer> counts = simulate(variants, 2_000);
        // B is paused — should receive 0 traffic. A and C split 50/50
        // (each ~1000).
        assertThat(counts.getOrDefault("B", 0)).isZero();
        assertThat(counts.get("A")).isBetween(900, 1_100);
        assertThat(counts.get("C")).isBetween(900, 1_100);
    }

    @Test
    void assignVariant_allWeightsNullOrZero_uniformFallback() {
        // No weights declared at all → fall back to a uniform distribution.
        // With 3 variants, each should get ~33.3%.
        List<TurChatFlow> variants = List.of(
                variant("A", "control", null),
                variant("B", "treatment", null),
                variant("C", "experimental", 0));
        Map<String, Integer> counts = simulate(variants, 3_000);
        for (String arm : List.of("A", "B", "C")) {
            assertThat(counts.get(arm))
                    .as("arm %s expected ~33%% on uniform fallback", arm)
                    .isBetween(900, 1_100);
        }
    }

    @Test
    void assignVariant_singleVariantAlwaysReturnsIt() {
        List<TurChatFlow> variants = List.of(variant("solo", "only", 100));
        for (int i = 0; i < 100; i++) {
            assertThat(TurChatFlowEngineService.assignVariant(variants, "conv-" + i, KEY).getId())
                    .isEqualTo("solo");
        }
    }

    @Test
    void assignVariant_negativeHashesStillBucketCorrectly() {
        // Math.floorMod (not %) ensures negative hashes land in [0, N).
        // A naive `hash % N` would yield negative indices and crash on
        // List.get; this test catches that regression.
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "treatment", 50));
        // Pick conversation ids whose hashCode is negative — at least one
        // assignment must succeed without throwing.
        for (int i = 0; i < 200; i++) {
            String conv = "conv-" + i;
            if (conv.hashCode() < 0) {
                TurChatFlow assigned = TurChatFlowEngineService.assignVariant(variants, conv, KEY);
                assertThat(assigned).isNotNull();
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("no negative-hash conv id sampled in 200 attempts");
    }

    // ─────────────────────────── Scheduling ───────────────────────────

    @Test
    void isInExperimentWindow_nullBoundsMeanAlwaysOpen() {
        TurChatFlow f = variant("A", "control", 50);
        assertThat(TurChatFlowEngineService.isInExperimentWindow(f, FIXED_NOW))
                .as("Null window bounds → variant always eligible")
                .isTrue();
    }

    @Test
    void isInExperimentWindow_beforeStart_excluded() {
        TurChatFlow f = variant("A", "control", 50);
        Instant now = FIXED_NOW;
        f.setExperimentStartsAt(now.plus(1, ChronoUnit.HOURS));
        assertThat(TurChatFlowEngineService.isInExperimentWindow(f, now)).isFalse();
    }

    @Test
    void isInExperimentWindow_afterEnd_excluded() {
        TurChatFlow f = variant("A", "control", 50);
        Instant now = FIXED_NOW;
        f.setExperimentEndsAt(now.minus(1, ChronoUnit.HOURS));
        assertThat(TurChatFlowEngineService.isInExperimentWindow(f, now)).isFalse();
    }

    @Test
    void isInExperimentWindow_withinWindow_included() {
        TurChatFlow f = variant("A", "control", 50);
        Instant now = FIXED_NOW;
        f.setExperimentStartsAt(now.minus(1, ChronoUnit.HOURS));
        f.setExperimentEndsAt(now.plus(1, ChronoUnit.HOURS));
        assertThat(TurChatFlowEngineService.isInExperimentWindow(f, now)).isTrue();
    }

    // ──────────────────── T73: forced variant (QA / demo) ────────────────────

    @Test
    void forceVariant_matchesLabelCaseInsensitively() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "lucas-alumni", 50));
        assertThat(TurChatFlowEngineService.forceVariant(variants, "lucas-alumni"))
                .as("exact label match").extracting(TurChatFlow::getId).isEqualTo("B");
        assertThat(TurChatFlowEngineService.forceVariant(variants, "LUCAS-Alumni"))
                .as("case-insensitive match").extracting(TurChatFlow::getId).isEqualTo("B");
        assertThat(TurChatFlowEngineService.forceVariant(variants, "  control  "))
                .as("surrounding whitespace trimmed").extracting(TurChatFlow::getId).isEqualTo("A");
    }

    @Test
    void forceVariant_unknownLabelReturnsNull() {
        List<TurChatFlow> variants = List.of(
                variant("A", "control", 50),
                variant("B", "treatment", 50));
        assertThat(TurChatFlowEngineService.forceVariant(variants, "does-not-exist")).isNull();
    }

    @Test
    void forceVariant_blankOrNullInputsReturnNull() {
        List<TurChatFlow> variants = List.of(variant("A", "control", 50));
        assertThat(TurChatFlowEngineService.forceVariant(variants, null)).isNull();
        assertThat(TurChatFlowEngineService.forceVariant(variants, "   ")).isNull();
        assertThat(TurChatFlowEngineService.forceVariant(null, "control")).isNull();
        assertThat(TurChatFlowEngineService.forceVariant(List.of(), "control")).isNull();
    }

    @Test
    void forceVariant_toleratesNullVariantLabel() {
        TurChatFlow noLabel = variant("A", null, 50);
        TurChatFlow labelled = variant("B", "treatment", 50);
        assertThat(TurChatFlowEngineService.forceVariant(List.of(noLabel, labelled), "treatment"))
                .extracting(TurChatFlow::getId).isEqualTo("B");
        assertThat(TurChatFlowEngineService.forceVariant(List.of(noLabel), "treatment")).isNull();
    }

    @Test
    void forceVariant_returnsFirstMatchOnDuplicateLabels() {
        List<TurChatFlow> variants = List.of(
                variant("A", "treatment", 50),
                variant("B", "treatment", 50));
        assertThat(TurChatFlowEngineService.forceVariant(variants, "treatment"))
                .extracting(TurChatFlow::getId).isEqualTo("A");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static TurChatFlow variant(String id, String label, Integer weight) {
        TurChatFlow f = new TurChatFlow();
        f.setId(id);
        f.setName(id);
        f.setExperimentKey(KEY);
        f.setVariantLabel(label);
        f.setTrafficWeight(weight);
        return f;
    }

    /**
     * Drives {@code n} synthetic conversations through {@link
     * TurChatFlowEngineService#assignVariant} and returns the per-variant
     * count. UUIDs as conversation ids give a uniform-ish hash spread —
     * fine for the ±5% tolerance the assertions allow.
     */
    private static Map<String, Integer> simulate(List<TurChatFlow> variants, int n) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String conv = UUID.randomUUID().toString();
            TurChatFlow assigned = TurChatFlowEngineService.assignVariant(variants, conv, KEY);
            counts.merge(assigned.getId(), 1, Integer::sum);
        }
        return counts;
    }
}
