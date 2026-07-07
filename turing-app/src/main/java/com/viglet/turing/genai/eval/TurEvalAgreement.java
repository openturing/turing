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

import java.util.List;

/**
 * T594 / §XXXIII.9 — pure, LLM-free inter-annotator agreement math used to make
 * the human-review layer (T592/T593) <b>trustworthy</b>:
 *
 * <ul>
 *   <li>{@link #fleiss(List, int)} — Fleiss' kappa over N items each rated by a
 *       (possibly variable) number of reviewers into {@code k} categories; the
 *       standard N-reviewer agreement metric.</li>
 *   <li>{@link #cohen(int[][])} — Cohen's kappa over a k×k confusion matrix of
 *       two raters; used for the MODEL-vs-human calibration audit (the two raters
 *       being the LLM judge and the human consensus).</li>
 *   <li>{@link #interpret(Double)} — the Landis &amp; Koch strength band.</li>
 * </ul>
 *
 * <p>Everything here is deterministic and dependency-free so it runs in CI at
 * zero cost, matching the block's design invariant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEvalAgreement {

    private TurEvalAgreement() {
    }

    /**
     * The result of an agreement computation.
     *
     * @param items             number of items that contributed (each rated by
     *                          &ge; 2 raters)
     * @param minRaters         smallest reviewer count across the items
     * @param maxRaters         largest reviewer count across the items
     * @param percentAgreement  mean observed agreement in [0,1] (null when no
     *                          item had &ge; 2 raters)
     * @param kappa             chance-corrected agreement (null when undefined —
     *                          no items, or perfect expected agreement)
     * @param interpretation    the Landis &amp; Koch strength band for {@code kappa}
     */
    public record AgreementResult(int items, int minRaters, int maxRaters,
            Double percentAgreement, Double kappa, String interpretation) {

        /** An empty result (not enough data to compute agreement). */
        public static AgreementResult empty() {
            return new AgreementResult(0, 0, 0, null, null, "n/a");
        }
    }

    /**
     * Fleiss' kappa (extended to a variable number of raters per item). Each
     * {@code int[]} in {@code items} holds the per-category rating counts for one
     * item; its length must equal {@code categories}. Items rated by fewer than
     * two reviewers are ignored (they carry no agreement signal).
     *
     * @param items      per-item category-count vectors
     * @param categories the number of rating categories (e.g. 2 for pass/fail)
     * @return the agreement result (never null)
     */
    public static AgreementResult fleiss(List<int[]> items, int categories) {
        if (items == null || items.isEmpty() || categories < 2) {
            return AgreementResult.empty();
        }
        int usedItems = 0;
        int minRaters = Integer.MAX_VALUE;
        int maxRaters = 0;
        double sumPi = 0d;
        long[] categoryTotals = new long[categories];
        long totalAssignments = 0;

        for (int[] counts : items) {
            if (counts == null || counts.length != categories) {
                continue;
            }
            int n = 0;
            long sumSquares = 0;
            for (int c = 0; c < categories; c++) {
                int cnt = counts[c];
                n += cnt;
                sumSquares += (long) cnt * cnt;
                categoryTotals[c] += cnt;
            }
            if (n < 2) {
                continue;
            }
            usedItems++;
            minRaters = Math.min(minRaters, n);
            maxRaters = Math.max(maxRaters, n);
            totalAssignments += n;
            // Observed agreement for this item: proportion of agreeing rater pairs.
            double pi = (sumSquares - n) / ((double) n * (n - 1));
            sumPi += pi;
        }

        if (usedItems == 0 || totalAssignments == 0) {
            return AgreementResult.empty();
        }

        double pBar = sumPi / usedItems;
        double pe = 0d;
        for (int c = 0; c < categories; c++) {
            double pj = categoryTotals[c] / (double) totalAssignments;
            pe += pj * pj;
        }
        Double kappa = kappaFrom(pBar, pe);
        return new AgreementResult(usedItems, minRaters, maxRaters, pBar, kappa, interpret(kappa));
    }

    /**
     * Cohen's kappa over a square confusion matrix {@code confusion[a][b]} =
     * number of items rater&nbsp;A put in category {@code a} and rater&nbsp;B in
     * category {@code b}.
     *
     * @param confusion a k×k confusion matrix
     * @return the agreement result (never null)
     */
    public static AgreementResult cohen(int[][] confusion) {
        if (confusion == null || confusion.length == 0) {
            return AgreementResult.empty();
        }
        int k = confusion.length;
        long total = 0;
        long diagonal = 0;
        long[] rowTotals = new long[k];
        long[] colTotals = new long[k];
        for (int a = 0; a < k; a++) {
            if (confusion[a] == null || confusion[a].length != k) {
                return AgreementResult.empty();
            }
            for (int b = 0; b < k; b++) {
                int v = confusion[a][b];
                total += v;
                rowTotals[a] += v;
                colTotals[b] += v;
                if (a == b) {
                    diagonal += v;
                }
            }
        }
        if (total == 0) {
            return AgreementResult.empty();
        }
        double po = diagonal / (double) total;
        double pe = 0d;
        for (int i = 0; i < k; i++) {
            pe += (rowTotals[i] / (double) total) * (colTotals[i] / (double) total);
        }
        Double kappa = kappaFrom(po, pe);
        return new AgreementResult((int) total, 2, 2, po, kappa, interpret(kappa));
    }

    /** {@code (observed - expected) / (1 - expected)}, or null when undefined. */
    private static Double kappaFrom(double observed, double expected) {
        double denom = 1d - expected;
        if (Math.abs(denom) < 1e-9) {
            // Expected agreement is total; kappa is undefined. Treat full observed
            // agreement as perfect, anything less as no signal.
            return observed >= 1d - 1e-9 ? 1d : null;
        }
        return (observed - expected) / denom;
    }

    /**
     * The Landis &amp; Koch strength-of-agreement band for a kappa value.
     *
     * @param kappa the kappa (may be null)
     * @return a human label ({@code "n/a"} when null)
     */
    public static String interpret(Double kappa) {
        if (kappa == null) {
            return "n/a";
        }
        double k = kappa;
        if (k < 0d) {
            return "poor";
        }
        if (k <= 0.20d) {
            return "slight";
        }
        if (k <= 0.40d) {
            return "fair";
        }
        if (k <= 0.60d) {
            return "moderate";
        }
        if (k <= 0.80d) {
            return "substantial";
        }
        return "almost-perfect";
    }
}
