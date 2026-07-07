/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader.builtin;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T591 / §XXXIII.6 — reference-answer CODE grader: embeds the final answer and a
 * golden {@code reference} with the platform's configured embedding provider
 * (via {@link TurRagContextBuilder}) and scores their cosine similarity. Passes
 * at/above {@code minSimilarity} (default 0.8). Bridges eval to the Block M
 * grounding contract without an LLM call.
 *
 * <p>The reference comes from {@code configJson} today; once datasets land
 * (T595) it can come from the bound dataset row's golden answer. Applies only
 * when a reference is configured <em>and</em> a default embedding model exists;
 * fails closed when embeddings can't be built.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurEmbeddingSimilarityGrader implements TurEvalGrader {

    private final TurRagContextBuilder ragContextBuilder;
    private final TurGlobalSettingsService globalSettingsService;

    public TurEmbeddingSimilarityGrader(TurRagContextBuilder ragContextBuilder,
            TurGlobalSettingsService globalSettingsService) {
        this.ragContextBuilder = ragContextBuilder;
        this.globalSettingsService = globalSettingsService;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.EMBEDDING_SIMILARITY;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return resolveReference(ctx, config) != null
                && StringUtils.hasText(globalSettingsService.getDefaultEmbeddingModelId());
    }

    /** Config {@code reference}, else the bound dataset row's golden answer (T595). */
    private static String resolveReference(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String reference = config.configString("reference");
        return reference != null ? reference : ctx.referenceAnswer();
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String reference = resolveReference(ctx, config);
        String answer = ctx.finalAnswer();
        if (answer == null || answer.isBlank()) {
            return TurEvalGraderResult.scored(0d, false, "fail", "empty answer to compare");
        }
        RagInfrastructure infra = ragContextBuilder.buildFromGlobalSettings().orElse(null);
        if (infra == null || infra.embeddingModel() == null) {
            return TurEvalGraderResult.scored(0d, false, "fail", "embedding provider unavailable");
        }
        try {
            EmbeddingModel embeddingModel = infra.embeddingModel();
            double cosine = cosine(embeddingModel.embed(answer), embeddingModel.embed(reference));
            double score = clamp01(cosine);
            double min = config.configDouble("minSimilarity", 0.8d);
            boolean passed = score >= min;
            String pct = String.format("%.3f", score);
            return TurEvalGraderResult.scored(score, passed, passed ? "pass" : "fail",
                    passed ? null : "cosine similarity " + pct + " below " + min);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] embedding-similarity failed: {}", e.getMessage());
            return TurEvalGraderResult.scored(0d, false, "fail", "embedding error: " + e.getMessage());
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0d || normB == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double clamp01(double v) {
        if (v < 0d) {
            return 0d;
        }
        return Math.min(v, 1d);
    }
}
