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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.eval.grader.builtin.TurNodeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurOutcomeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurRubricJudgeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurSlotMatchGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;

/**
 * T586 / §XXXIII.1 — unit coverage for the grader SPI: the CODE built-ins
 * (slot / outcome / node, pure and CI-safe) reproduce the historical scoring,
 * and the registry resolves the legacy default stack.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEvalGraderTest {

    private final TurSlotMatchGrader slot = new TurSlotMatchGrader();
    private final TurOutcomeGrader outcome = new TurOutcomeGrader();
    private final TurNodeGrader node = new TurNodeGrader();

    private static TurEvalGradingContext ctx(TurAgentEvalCase c, Map<String, String> slots,
            String finalNodeId, String actualOutcome) {
        return new TurEvalGradingContext(c, List.of(), List.of(), slots, finalNodeId, actualOutcome, null);
    }

    @Test
    void registryResolvesLegacyDefaultStackInOrder() {
        TurEvalGraderRegistry registry = new TurEvalGraderRegistry(
                List.of(slot, outcome, node, new TurRubricJudgeGrader()));

        List<TurEvalGrader> stack = registry.resolveGraders(new TurAgentEvalCase());

        assertThat(stack).extracting(TurEvalGrader::graderId).containsExactly(
                TurEvalBuiltinGraders.SLOT_MATCH, TurEvalBuiltinGraders.OUTCOME,
                TurEvalBuiltinGraders.NODE, TurEvalBuiltinGraders.RUBRIC);
        assertThat(registry.find(TurEvalBuiltinGraders.SLOT_MATCH)).isPresent();
        assertThat(slot.kind()).isEqualTo(TurEvalGraderKind.CODE);
    }

    @Test
    void slotGraderSkippedWhenNoExpectedSlots() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        assertThat(slot.appliesTo(ctx(c, Map.of(), null, null))).isFalse();
    }

    @Test
    void slotGraderScoresPartialMatchWithDetails() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedSlotsJson("{\"name\":\"Ada\",\"email\":\"a@b.com\"}");
        TurEvalGradingContext ctx = ctx(c, Map.of("name", " Ada ", "email", "wrong@x.com"), null, null);

        assertThat(slot.appliesTo(ctx)).isTrue();
        TurEvalGraderResult r = slot.grade(ctx);
        assertThat(r.score()).isEqualTo(0.5d);
        assertThat(r.passed()).isFalse();
        assertThat(r.details()).hasSize(2);
        assertThat(r.details()).anySatisfy(d -> {
            assertThat(d.key()).isEqualTo("name");
            assertThat(d.match()).isTrue();
        });
    }

    @Test
    void slotGraderPassesWhenAllMatchAfterNormalization() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedSlotsJson("{\"name\":\"Ada\"}");
        TurEvalGraderResult r = slot.grade(ctx(c, Map.of("name", "  Ada"), null, null));
        assertThat(r.score()).isEqualTo(1d);
        assertThat(r.passed()).isTrue();
    }

    @Test
    void outcomeGraderSkippedForAny() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);
        assertThat(outcome.appliesTo(ctx(c, Map.of(), null, "CAPTURED"))).isFalse();
    }

    @Test
    void outcomeGraderMatchesLabel() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.CAPTURED);
        assertThat(outcome.appliesTo(ctx(c, Map.of(), null, "CAPTURED"))).isTrue();
        assertThat(outcome.grade(ctx(c, Map.of(), null, "CAPTURED")).passed()).isTrue();
        assertThat(outcome.grade(ctx(c, Map.of(), null, "ABANDONED")).passed()).isFalse();
    }

    @Test
    void nodeGraderMatchesCursor() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setExpectedNodeId("collect-email");
        assertThat(node.appliesTo(ctx(c, Map.of(), "collect-email", null))).isTrue();
        assertThat(node.grade(ctx(c, Map.of(), "collect-email", null)).passed()).isTrue();
        assertThat(node.grade(ctx(c, Map.of(), "welcome", null)).passed()).isFalse();
    }

    @Test
    void nodeGraderSkippedWhenNoExpectedNode() {
        assertThat(node.appliesTo(ctx(new TurAgentEvalCase(), Map.of(), "x", null))).isFalse();
    }

    @Test
    void resultFactoriesProduceCanonicalShapes() {
        assertThat(TurEvalGraderResult.binary(true).score()).isEqualTo(1d);
        assertThat(TurEvalGraderResult.binary(false).verdict()).isEqualTo("fail");
        assertThat(TurEvalGraderResult.deferred("later").deferred()).isTrue();
    }
}
