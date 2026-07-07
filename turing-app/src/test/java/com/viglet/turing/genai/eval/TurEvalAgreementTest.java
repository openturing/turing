/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.eval.TurEvalAgreement.AgreementResult;

/**
 * T594 / §XXXIII.9 — the pure inter-annotator agreement math (Fleiss' /
 * Cohen's kappa). LLM-free, so it runs in CI at zero cost.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEvalAgreementTest {

    @Test
    void fleissPerfectAgreementIsKappaOne() {
        // 3 items, 3 raters each, all agreeing (2 pass items, 1 fail item).
        AgreementResult r = TurEvalAgreement.fleiss(
                List.of(new int[] { 0, 3 }, new int[] { 0, 3 }, new int[] { 3, 0 }), 2);
        assertThat(r.items()).isEqualTo(3);
        assertThat(r.minRaters()).isEqualTo(3);
        assertThat(r.maxRaters()).isEqualTo(3);
        assertThat(r.percentAgreement()).isEqualTo(1d);
        assertThat(r.kappa()).isEqualTo(1d);
        assertThat(r.interpretation()).isEqualTo("almost-perfect");
    }

    @Test
    void fleissMaximalDisagreementIsNonPositive() {
        // Every item split evenly — observed agreement below chance.
        AgreementResult r = TurEvalAgreement.fleiss(
                List.of(new int[] { 1, 1 }, new int[] { 1, 1 }, new int[] { 1, 1 }), 2);
        assertThat(r.kappa()).isNotNull();
        assertThat(r.kappa()).isLessThanOrEqualTo(0d);
        assertThat(r.percentAgreement()).isEqualTo(0d);
    }

    @Test
    void fleissIgnoresSingleRaterItems() {
        AgreementResult r = TurEvalAgreement.fleiss(
                List.of(new int[] { 0, 1 }, new int[] { 0, 3 }, new int[] { 3, 0 }), 2);
        // The 1-rater item is dropped; 2 items contribute.
        assertThat(r.items()).isEqualTo(2);
    }

    @Test
    void fleissEmptyOrTooFewIsEmpty() {
        assertThat(TurEvalAgreement.fleiss(List.of(), 2).kappa()).isNull();
        assertThat(TurEvalAgreement.fleiss(List.of(new int[] { 0, 1 }), 2).items()).isZero();
    }

    @Test
    void cohenClassicTextbookValue() {
        // Confusion [[20,5],[10,15]] → po=0.7, pe=0.5, kappa=0.4.
        AgreementResult r = TurEvalAgreement.cohen(new int[][] { { 20, 5 }, { 10, 15 } });
        assertThat(r.percentAgreement()).isCloseTo(0.7d, within(1e-9));
        assertThat(r.kappa()).isCloseTo(0.4d, within(1e-9));
        assertThat(r.interpretation()).isEqualTo("fair");
        assertThat(r.items()).isEqualTo(50);
    }

    @Test
    void cohenPerfectAgreement() {
        AgreementResult r = TurEvalAgreement.cohen(new int[][] { { 8, 0 }, { 0, 12 } });
        assertThat(r.kappa()).isEqualTo(1d);
        assertThat(r.interpretation()).isEqualTo("almost-perfect");
    }

    @Test
    void interpretBands() {
        assertThat(TurEvalAgreement.interpret(-0.1)).isEqualTo("poor");
        assertThat(TurEvalAgreement.interpret(0.1)).isEqualTo("slight");
        assertThat(TurEvalAgreement.interpret(0.3)).isEqualTo("fair");
        assertThat(TurEvalAgreement.interpret(0.5)).isEqualTo("moderate");
        assertThat(TurEvalAgreement.interpret(0.7)).isEqualTo("substantial");
        assertThat(TurEvalAgreement.interpret(0.9)).isEqualTo("almost-perfect");
        assertThat(TurEvalAgreement.interpret(null)).isEqualTo("n/a");
    }
}
