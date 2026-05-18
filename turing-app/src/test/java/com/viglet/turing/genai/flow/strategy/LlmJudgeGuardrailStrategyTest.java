/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.strategy.LlmJudgeGuardrailStrategy.JudgeVerdict;

/**
 * Pure-function tests for the LLM-judge guardrail heuristics that decide
 * whether to force-capture a rejected user message as the slot value or
 * trust the judge's redirect and stay on the node.
 *
 * <p>Pins the regression observed in production where the visitor clicked
 * a non-name starter chip on the {@code ai-name-lucas} node, the judge
 * correctly rejected twice with {@code redirect_message="Vamos falar sobre
 * o seu nome primeiro?"}, but the engine force-captured the chip text as
 * the {@code name} slot anyway. The fix: trust a substantive
 * {@code redirect_message} on both verdicts as a deliberate "stay on
 * topic" signal rather than a judge flake.
 *
 * <p>No Spring context, no mocks — exercises only the package-private
 * {@code isNonBlankRedirect} predicate and the {@code JudgeVerdict}
 * record. Suite stays under 50ms.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.x
 */
class LlmJudgeGuardrailStrategyTest {

    // ─────────────────────────── isNonBlankRedirect ───────────────────────────

    @Test
    void isNonBlankRedirect_acceptsSubstantiveRedirect() {
        JudgeVerdict v = verdictWithRedirect("Vamos falar sobre o seu nome primeiro?");
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(v)).isTrue();
    }

    @Test
    void isNonBlankRedirect_rejectsNullMessage() {
        JudgeVerdict v = verdictWithRedirect(null);
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(v)).isFalse();
    }

    @Test
    void isNonBlankRedirect_rejectsEmptyString() {
        JudgeVerdict v = verdictWithRedirect("");
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(v)).isFalse();
    }

    @Test
    void isNonBlankRedirect_rejectsWhitespaceOnly() {
        JudgeVerdict v = verdictWithRedirect("   \t  ");
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(v)).isFalse();
    }

    @Test
    void isNonBlankRedirect_rejectsShortPlaceholders() {
        // Threshold is 8 chars after trim — filters one-word fallbacks
        // ("stay", "no", "skip") that some prompts emit when the model
        // has no real reason to redirect. These shouldn't count as
        // confidence signals; force-capture should still kick in.
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect("stay"))).isFalse();
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect("no"))).isFalse();
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect("skip"))).isFalse();
    }

    @Test
    void isNonBlankRedirect_trimsBeforeMeasuringLength() {
        // 7 chars surrounded by whitespace → effective length 7, below threshold.
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect("  abcdefg  "))).isFalse();
        // 8 chars surrounded by whitespace → effective length 8, on threshold.
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect("  abcdefgh  "))).isTrue();
    }

    @Test
    void isNonBlankRedirect_acceptsTypicalRedirects() {
        // Real-world redirect_message strings emitted by the judge prompt.
        // All should pass — they're the signal we want to trust.
        String[] real = {
                "Vamos falar sobre o seu nome primeiro?",
                "Por favor, informe seu cargo atual.",
                "Me conta qual é a sua área de interesse.",
                "Qual é a razão social da empresa?",
        };
        for (String r : real) {
            assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(verdictWithRedirect(r)))
                    .as("redirect_message=%s", r)
                    .isTrue();
        }
    }

    // ─────────────────────────── Force-capture regression scenarios ───────────

    @Test
    void verdict_judgeFlakeWithBlankRedirect_qualifiesForForceCapture() {
        // Mode (a): judge rejected without articulating WHY. This is the
        // pattern we want force-capture to handle — gpt-4o-mini ~5%
        // false-reject rate. Predicate must return false (= force-capture
        // path is allowed downstream).
        JudgeVerdict flake = new JudgeVerdict(
                /* onTopic        */ false,
                /* collectedValue */ null,
                /* readyToAdvance */ false,
                /* redirectMessage*/ null,
                /* abandoned      */ false);
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(flake)).isFalse();
    }

    @Test
    void verdict_userOffTopicWithSubstantiveRedirect_inhibitsForceCapture() {
        // Mode (b): user actually off-topic — judge correctly produced a
        // substantive redirect explaining the gap. Predicate must return
        // true (= force-capture path is blocked downstream; engine stays
        // on the node and shows the redirect to the visitor).
        JudgeVerdict offTopic = new JudgeVerdict(
                /* onTopic        */ false,
                /* collectedValue */ null,
                /* readyToAdvance */ false,
                /* redirectMessage*/ "Vamos falar sobre o seu nome primeiro?",
                /* abandoned      */ false);
        assertThat(LlmJudgeGuardrailStrategy.isNonBlankRedirect(offTopic)).isTrue();
    }

    // ─────────────────────────── resolveRejectPolicy ───────────────────────────

    @Test
    void resolveRejectPolicy_nullField_defaultsToReprompt() {
        // Backwards-compat: existing flows without the field keep the
        // pre-2026.2.31 behavior (trust judge's redirect, force-capture
        // only when blank).
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy(null)))
                .isEqualTo("reprompt");
    }

    @Test
    void resolveRejectPolicy_blankField_defaultsToReprompt() {
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("")))
                .isEqualTo("reprompt");
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("   ")))
                .isEqualTo("reprompt");
    }

    @Test
    void resolveRejectPolicy_advanceWithLiteral_recognized() {
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("advance_with_literal")))
                .isEqualTo("advance_with_literal");
    }

    @Test
    void resolveRejectPolicy_block_recognized() {
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("block")))
                .isEqualTo("block");
    }

    @Test
    void resolveRejectPolicy_explicitReprompt_recognized() {
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("reprompt")))
                .isEqualTo("reprompt");
    }

    @Test
    void resolveRejectPolicy_isCaseInsensitive() {
        // Flow JSON authors may write either snake_case or SHOUTING_SNAKE_CASE —
        // both must work without surprise. Normalization is lowercase.
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("ADVANCE_WITH_LITERAL")))
                .isEqualTo("advance_with_literal");
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("Block")))
                .isEqualTo("block");
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("  Advance_With_Literal  ")))
                .isEqualTo("advance_with_literal");
    }

    @Test
    void resolveRejectPolicy_unknownValue_defaultsToRepromptWithWarning() {
        // Defensive: a typo or future-but-unreleased value must not blow
        // up the engine — fall back to the safe default (no surprise
        // force-capture) and log a warning so the misconfiguration is
        // visible in ops.
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("YOLO")))
                .isEqualTo("reprompt");
        assertThat(LlmJudgeGuardrailStrategy.resolveRejectPolicy(nodeWithPolicy("advance")))
                .isEqualTo("reprompt");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static JudgeVerdict verdictWithRedirect(String redirect) {
        // Other fields don't matter for isNonBlankRedirect — set to the
        // "rejected, no value, not abandoned" shape that's the only state
        // where the predicate is consulted in production.
        return new JudgeVerdict(false, null, false, redirect, false);
    }

    /**
     * Build a minimal {@link ChatFlowNode} with only {@code id} and
     * {@code onJudgeReject} set — every other NodeData field is null. The
     * resolver under test reads only those two, so the rest can stay
     * unset without affecting outcomes.
     */
    private static ChatFlowNode nodeWithPolicy(String policy) {
        ChatFlowNode.NodeData data = new ChatFlowNode.NodeData(
                /* label                 */ null,
                /* type                  */ "aiQuestion",
                /* aiInstruction         */ null,
                /* outputVariable        */ "slot_x",
                /* validationRule        */ null,
                /* functionName          */ null,
                /* conditionExpression   */ null,
                /* toolSource            */ null,
                /* mcpServerId           */ null,
                /* subFlowId             */ null,
                /* subFlowName           */ null,
                /* personaId             */ null,
                /* switchVariable        */ null,
                /* switchOptions         */ null,
                /* inlineOptions         */ null,
                /* overrideExistingValue */ null,
                /* slotName              */ null,
                /* slotOperation         */ null,
                /* slotValue             */ null,
                /* onJudgeReject         */ policy);
        return new ChatFlowNode("test-node", "aiQuestion", data);
    }
}
