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

    /* §I.5 step 6 / T16 — deleted: 9 tests for {@code isNonBlankRedirect}
     * heuristic + 2 force-capture regression scenarios that exercised the
     * same predicate. The heuristic was removed when the second-opinion
     * retry block (patch #21) was deleted in T16 — {@code onJudgeReject}
     * declarative policy (#22) is the only reject driver now. Tests
     * recoverable from git log on commits before T16.
     */

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
                /* onJudgeReject         */ policy,
                /* toolsEnabled          */ null,
                /* requiredTools         */ null,
                /* routineId             */ null,
                /* routineTimeoutMs      */ null);
        return new ChatFlowNode("test-node", "aiQuestion", data);
    }
}
