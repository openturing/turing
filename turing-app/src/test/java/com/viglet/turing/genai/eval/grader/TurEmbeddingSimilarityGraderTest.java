/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.eval.grader.builtin.TurEmbeddingSimilarityGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T591 / §XXXIII.6 — reference-answer embedding-cosine grader: applies-gating on
 * a reference + configured embedding model, cosine scoring vs a threshold, and
 * fail-closed when embeddings are unavailable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEmbeddingSimilarityGraderTest {

    private final TurRagContextBuilder ragContextBuilder = mock(TurRagContextBuilder.class);
    private final TurGlobalSettingsService globalSettings = mock(TurGlobalSettingsService.class);
    private final TurEmbeddingSimilarityGrader grader =
            new TurEmbeddingSimilarityGrader(ragContextBuilder, globalSettings);

    private static TurEvalGradingContext ctx(String answer) {
        return new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1", List.of(),
                List.of(answer), Map.of(), null, null, null);
    }

    private static TurEvalGraderConfigView cfg(String json) {
        return new TurEvalGraderConfigView("embedding-similarity", "es", "CODE", json, 1d, 0d, 1);
    }

    private void withEmbeddings(float[] answerVec, float[] referenceVec) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed("the answer")).thenReturn(answerVec);
        when(model.embed("the reference")).thenReturn(referenceVec);
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(
                Optional.of(new RagInfrastructure(null, model, null, null, null, null)));
    }

    @Test
    void appliesOnlyWithReferenceAndConfiguredEmbeddingModel() {
        when(globalSettings.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        assertThat(grader.appliesTo(ctx("a"), cfg("{}"))).isFalse();
        assertThat(grader.appliesTo(ctx("a"), cfg("{\"reference\":\"x\"}"))).isTrue();
        when(globalSettings.getDefaultEmbeddingModelId()).thenReturn("");
        assertThat(grader.appliesTo(ctx("a"), cfg("{\"reference\":\"x\"}"))).isFalse();
    }

    @Test
    void identicalVectorsScoreOnePass() {
        withEmbeddings(new float[] {1f, 0f, 0f}, new float[] {1f, 0f, 0f});
        TurEvalGraderResult r = grader.grade(ctx("the answer"), cfg("{\"reference\":\"the reference\"}"));
        assertThat(r.score()).isEqualTo(1d);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void orthogonalVectorsScoreZeroFail() {
        withEmbeddings(new float[] {1f, 0f, 0f}, new float[] {0f, 1f, 0f});
        TurEvalGraderResult r = grader.grade(ctx("the answer"), cfg("{\"reference\":\"the reference\"}"));
        assertThat(r.score()).isEqualTo(0d);
        assertThat(r.passed()).isFalse();
    }

    @Test
    void thresholdIsConfigurable() {
        withEmbeddings(new float[] {1f, 0f, 0f}, new float[] {0.9f, 0.1f, 0f});
        // cosine ~ 0.994 — passes default 0.8, fails a 0.999 bar
        assertThat(grader.grade(ctx("the answer"),
                cfg("{\"reference\":\"the reference\"}")).passed()).isTrue();
        assertThat(grader.grade(ctx("the answer"),
                cfg("{\"reference\":\"the reference\",\"minSimilarity\":0.999}")).passed()).isFalse();
    }

    @Test
    void failsClosedWhenEmbeddingsUnavailable() {
        when(ragContextBuilder.buildFromGlobalSettings()).thenReturn(Optional.empty());
        TurEvalGraderResult r = grader.grade(ctx("the answer"), cfg("{\"reference\":\"x\"}"));
        assertThat(r.passed()).isFalse();
        assertThat(r.rationale()).contains("unavailable");
    }

    @Test
    void failsClosedOnEmptyAnswer() {
        assertThat(grader.grade(ctx("  "), cfg("{\"reference\":\"x\"}")).passed()).isFalse();
    }

    @Test
    void fallsBackToDatasetReferenceWhenConfigHasNone() {
        when(globalSettings.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        withEmbeddings(new float[] {1f, 0f, 0f}, new float[] {1f, 0f, 0f});
        // config carries no reference, but the bound dataset row supplies one (T595)
        TurEvalGradingContext ctx = new TurEvalGradingContext(new TurAgentEvalCase(), "conv-1",
                List.of(), List.of("the answer"), Map.of(), null, null, null, "the reference");

        assertThat(grader.appliesTo(ctx, cfg("{}"))).isTrue();
        assertThat(grader.grade(ctx, cfg("{}")).passed()).isTrue();
    }
}
