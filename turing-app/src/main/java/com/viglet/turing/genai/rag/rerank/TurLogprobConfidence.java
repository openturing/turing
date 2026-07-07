/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.List;
import java.util.Locale;

/**
 * T180 / §X.13.c — the pure math that turns an OpenAI Responses {@code logprobs}
 * payload into a relevance <b>confidence</b> in {@code [0,1]} and blends it with
 * the retrieval (BM25 / vector) signal.
 *
 * <p>The LLM-logprobs reranker asks the model a single yes/no question — "is this
 * snippet relevant to the query?" — with {@code top_logprobs} enabled, then reads
 * the log-probabilities of the first answer token. {@code yes}/{@code no} carry
 * the model's calibrated confidence (an answer it hedged on lands near 0.5; a
 * confident one near 0/1), which {@link #blend} mixes into the retrieval score so
 * a hedged top hit is demoted rather than trusted.
 *
 * <p>This class is intentionally free of any OpenAI SDK type so it is trivially
 * unit-testable; the caller maps the SDK's {@code TopLogprob} list onto
 * {@link TokenLogprob} records.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurLogprobConfidence {

    private TurLogprobConfidence() {
    }

    /** One token alternative and its (natural-log) probability, as returned by the model. */
    public record TokenLogprob(String token, double logprob) {
    }

    /**
     * P(relevant) in {@code [0,1]} from the first answer token's alternatives.
     * Finds the {@code yes} / {@code no} mass (a token whose trimmed, lowercased
     * text starts with "yes"/"no"), converts each from log-space with
     * {@link Math#exp}, and normalizes {@code pYes / (pYes + pNo)}. When only one
     * polarity is present its probability (or its complement) is used; when
     * neither is present the result is the neutral {@code 0.5} (no signal), so the
     * blend falls back to the retrieval order.
     */
    public static double relevanceProbability(List<TokenLogprob> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return 0.5;
        }
        double pYes = -1;
        double pNo = -1;
        for (TokenLogprob alt : alternatives) {
            if (alt == null || alt.token() == null) {
                continue;
            }
            String token = alt.token().trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (pYes < 0 && token.startsWith("yes")) {
                pYes = clampProbability(Math.exp(alt.logprob()));
            } else if (pNo < 0 && token.startsWith("no")) {
                pNo = clampProbability(Math.exp(alt.logprob()));
            }
        }
        if (pYes < 0 && pNo < 0) {
            return 0.5;
        }
        if (pYes < 0) {
            return clampProbability(1.0 - pNo);
        }
        if (pNo < 0) {
            return pYes;
        }
        double mass = pYes + pNo;
        return mass <= 0 ? 0.5 : clampProbability(pYes / mass);
    }

    /**
     * The retrieval signal for a candidate at zero-based {@code rank}, as a
     * reciprocal-rank score in {@code (0,1]} ({@code 1/(1+rank)}). Used when the
     * candidate carries no explicit BM25 / vector score — the retrieval <em>order</em>
     * is the proxy, and it decays smoothly so the blend keeps a stable prior.
     */
    public static double reciprocalRankScore(int rank) {
        int r = Math.max(rank, 0);
        return 1.0 / (1.0 + r);
    }

    /**
     * Convex blend of the retrieval score and the LLM confidence, both in
     * {@code [0,1]}: {@code weight * retrieval + (1 - weight) * confidence}.
     * {@code weight} is clamped to {@code [0,1]} (1 → retrieval only, the legacy
     * order; 0 → confidence only).
     */
    public static double blend(double retrievalScore, double confidence, double weight) {
        double w = clampProbability(weight);
        return w * clampProbability(retrievalScore) + (1.0 - w) * clampProbability(confidence);
    }

    private static double clampProbability(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }
}
