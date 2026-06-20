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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T28 / §III.5 — pins the orchestrator's cascade and mode selection.
 *
 * <p>Uses inline fake strategies (no Mockito) so the contract reads
 * directly: each fake tracks invocation count and returns the
 * pre-configured enrichment.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatIntentClassifierOrchestratorTest {

    @Test
    void autoModeCascadesFromLlmToLuceneWhenLlmReturnsUnclassified() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, true,
                TurChatSessionEnrichment.unclassified());
        FakeStrategy lucene = new FakeStrategy(TurLuceneIntentClassifier.TYPE, true,
                classifiedAs("Refund request"));

        var orchestrator = new TurChatIntentClassifier(List.of(llm, lucene), "auto", null);
        var result = orchestrator.classify("agent-1", "give me a refund", List.<Map<String, Object>>of());

        assertThat(result.goalSummary()).isEqualTo("Refund request");
        assertThat(llm.classifyCalls).isEqualTo(1);
        assertThat(lucene.classifyCalls).isEqualTo(1);
    }

    @Test
    void autoModeShortCircuitsWhenLlmClassifies() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, true,
                classifiedAs("LLM-derived label"));
        FakeStrategy lucene = new FakeStrategy(TurLuceneIntentClassifier.TYPE, true,
                classifiedAs("Should not be reached"));

        var orchestrator = new TurChatIntentClassifier(List.of(llm, lucene), "auto", null);
        var result = orchestrator.classify("agent-1", "any message", List.<Map<String, Object>>of());

        assertThat(result.goalSummary()).isEqualTo("LLM-derived label");
        assertThat(llm.classifyCalls).isEqualTo(1);
        assertThat(lucene.classifyCalls).isZero();
    }

    @Test
    void luceneModePinsToLuceneStrategy() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, true,
                classifiedAs("LLM should be skipped"));
        FakeStrategy lucene = new FakeStrategy(TurLuceneIntentClassifier.TYPE, true,
                classifiedAs("From Lucene"));

        var orchestrator = new TurChatIntentClassifier(List.of(llm, lucene), "lucene", null);
        var result = orchestrator.classify("agent-1", "any message", List.<Map<String, Object>>of());

        assertThat(result.goalSummary()).isEqualTo("From Lucene");
        assertThat(llm.classifyCalls).isZero();
        assertThat(lucene.classifyCalls).isEqualTo(1);
    }

    @Test
    void llmModeFallsBackToUnclassifiedWhenLlmUnavailable() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, false,
                classifiedAs("never returned"));
        FakeStrategy lucene = new FakeStrategy(TurLuceneIntentClassifier.TYPE, true,
                classifiedAs("but lucene-mode ignores Lucene"));

        var orchestrator = new TurChatIntentClassifier(List.of(llm, lucene), "llm", null);
        var result = orchestrator.classify("agent-1", "any message", List.<Map<String, Object>>of());

        assertThat(result.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
        assertThat(llm.classifyCalls).isZero();
        assertThat(lucene.classifyCalls).isZero();
    }

    @Test
    void isAvailableReflectsActiveModeRegistration() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, true,
                classifiedAs("x"));
        FakeStrategy lucene = new FakeStrategy(TurLuceneIntentClassifier.TYPE, true,
                classifiedAs("y"));

        assertThat(new TurChatIntentClassifier(List.of(llm), "auto", null).isAvailable()).isTrue();
        assertThat(new TurChatIntentClassifier(List.of(lucene), "lucene", null).isAvailable()).isTrue();
        assertThat(new TurChatIntentClassifier(List.of(lucene), "llm", null).isAvailable()).isFalse();
        assertThat(new TurChatIntentClassifier(List.of(), "auto", null).isAvailable()).isFalse();
    }

    @Test
    void backwardsCompatShimRoutesNullAgentId() {
        FakeStrategy llm = new FakeStrategy(TurLlmIntentClassifier.TYPE, true,
                classifiedAs("LLM doesn't need agent id"));

        var orchestrator = new TurChatIntentClassifier(List.of(llm), "auto", null);
        var result = orchestrator.classify("hello", List.<Map<String, Object>>of());

        assertThat(result.goalSummary()).isEqualTo("LLM doesn't need agent id");
        assertThat(llm.classifyCalls).isEqualTo(1);
        assertThat(llm.lastAgentId).isNull();
    }

    private static TurChatSessionEnrichment classifiedAs(String label) {
        return new TurChatSessionEnrichment(
                TurChatIntentLabel.OTHER,
                0.5,
                label,
                TurChatGoalAchieved.UNKNOWN,
                TurChatSentiment.UNKNOWN,
                List.of(),
                Instant.parse("2026-06-15T12:00:00Z"));
    }

    private static final class FakeStrategy implements TurIntentClassifierStrategy {
        private final String type;
        private final boolean available;
        private final TurChatSessionEnrichment result;
        private final List<String> seenAgentIds = new ArrayList<>();
        int classifyCalls;
        String lastAgentId;

        FakeStrategy(String type, boolean available, TurChatSessionEnrichment result) {
            this.type = type;
            this.available = available;
            this.result = result;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public boolean isAvailable(String agentId) {
            return available;
        }

        @Override
        public TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
                List<Map<String, Object>> messages) {
            classifyCalls++;
            lastAgentId = agentId;
            seenAgentIds.add(agentId);
            return result;
        }
    }
}
