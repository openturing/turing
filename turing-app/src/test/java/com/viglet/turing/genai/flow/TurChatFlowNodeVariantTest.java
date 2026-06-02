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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeVariant;

/**
 * Unit tests for per-node A/B (T72): the deterministic weighted-hash
 * {@link ChatFlowNode#pickNodeVariant} assignment, the
 * {@link ChatFlowNode#resolveVariant(String)} instruction swap, and the
 * {@link ChatFlowNode#formatNodeVariantTrace} forensic line. Pure-function
 * tests — no Spring context, no DB. Mirrors the flow-level
 * {@code TurChatFlowAbVariantAssignmentTest} so per-node and per-flow A/B
 * share one notion of sticky weighted bucketing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowNodeVariantTest {

    @Test
    void pickNodeVariant_returnsNull_whenNoExperiment() {
        List<NodeVariant> variants = List.of(new NodeVariant("a", 1, "ai"));
        assertThat(ChatFlowNode.pickNodeVariant(variants, "conv-1", null)).isNull();
        assertThat(ChatFlowNode.pickNodeVariant(variants, "conv-1", "   ")).isNull();
        assertThat(ChatFlowNode.pickNodeVariant(null, "conv-1", "exp")).isNull();
        assertThat(ChatFlowNode.pickNodeVariant(List.of(), "conv-1", "exp")).isNull();
    }

    @Test
    void pickNodeVariant_singleVariant_alwaysReturnsIt() {
        NodeVariant only = new NodeVariant("solo", null, "ai");
        assertThat(ChatFlowNode.pickNodeVariant(List.of(only), "conv-99", "exp")).isSameAs(only);
    }

    @Test
    void pickNodeVariant_isStickyPerConversation() {
        List<NodeVariant> variants = List.of(
                new NodeVariant("a", 50, "A"),
                new NodeVariant("b", 50, "B"));
        NodeVariant first = ChatFlowNode.pickNodeVariant(variants, "conv-sticky", "exp");
        for (int i = 0; i < 25; i++) {
            assertThat(ChatFlowNode.pickNodeVariant(variants, "conv-sticky", "exp"))
                    .as("Same conversation + key must always map to the same arm")
                    .isSameAs(first);
        }
    }

    @Test
    void pickNodeVariant_independentBuckets_perExperimentKey() {
        // A different node experiment key on the same conversation buckets
        // independently — node-level A/B never collides with flow-level A/B.
        List<NodeVariant> variants = List.of(
                new NodeVariant("a", 50, "A"),
                new NodeVariant("b", 50, "B"));
        long aForKey1 = 0;
        long aForKey2 = 0;
        for (int i = 0; i < 2000; i++) {
            String conv = "conv-" + i;
            if ("a".equals(ChatFlowNode.pickNodeVariant(variants, conv, "key-1").label())) aForKey1++;
            if ("a".equals(ChatFlowNode.pickNodeVariant(variants, conv, "key-2").label())) aForKey2++;
        }
        // Both keys split ~50/50 but the per-conversation arm differs between
        // keys for a meaningful fraction — they are not the same hash.
        assertThat(aForKey1).isBetween(800L, 1200L);
        assertThat(aForKey2).isBetween(800L, 1200L);
    }

    @Test
    void pickNodeVariant_50_50_splitsEvenly() {
        List<NodeVariant> variants = List.of(
                new NodeVariant("a", 50, "A"),
                new NodeVariant("b", 50, "B"));
        int aCount = 0;
        int total = 5000;
        for (int i = 0; i < total; i++) {
            if ("a".equals(ChatFlowNode.pickNodeVariant(variants, "conv-" + i, "exp").label())) {
                aCount++;
            }
        }
        // ±5% tolerance — same bar the flow-level assignment test uses.
        assertThat(aCount).isBetween((int) (total * 0.45), (int) (total * 0.55));
    }

    @Test
    void pickNodeVariant_excludesZeroWeightArm_amongNonZero() {
        List<NodeVariant> variants = List.of(
                new NodeVariant("paused", 0, "A"),
                new NodeVariant("live", 10, "B"));
        for (int i = 0; i < 1000; i++) {
            assertThat(ChatFlowNode.pickNodeVariant(variants, "conv-" + i, "exp").label())
                    .as("A zero-weight arm inside a non-zero experiment is never picked")
                    .isEqualTo("live");
        }
    }

    @Test
    void pickNodeVariant_allZeroWeights_fallsBackToUniform() {
        List<NodeVariant> variants = List.of(
                new NodeVariant("a", 0, "A"),
                new NodeVariant("b", 0, "B"));
        int aCount = 0;
        int total = 4000;
        for (int i = 0; i < total; i++) {
            if ("a".equals(ChatFlowNode.pickNodeVariant(variants, "conv-" + i, "exp").label())) {
                aCount++;
            }
        }
        // Uniform fallback distributes both arms roughly evenly.
        assertThat(aCount).isBetween((int) (total * 0.45), (int) (total * 0.55));
    }

    @Test
    void resolveVariant_returnsSameNode_whenNoExperiment() {
        ChatFlowNode plain = node("ai-cargo", "Base question?", null, null);
        assertThat(plain.resolveVariant("conv-1")).isSameAs(plain);
    }

    @Test
    void resolveVariant_swapsAiInstruction_andPreservesIdentityAndOtherFields() {
        // One-arm experiment so the chosen variant is deterministic.
        NodeVariant arm = new NodeVariant("punchy", 1, "Qual cargo você busca?");
        ChatFlowNode base = node("ai-cargo", "Me conte sobre o cargo desejado.",
                "node-cargo-wording", List.of(arm));

        ChatFlowNode effective = base.resolveVariant("conv-xyz");

        assertThat(effective).isNotSameAs(base);
        assertThat(effective.id()).isEqualTo("ai-cargo");
        assertThat(effective.type()).isEqualTo("aiQuestion");
        assertThat(effective.aiInstruction()).isEqualTo("Qual cargo você busca?");
        // Experiment metadata survives the swap so the trace can re-resolve.
        assertThat(effective.nodeExperimentKey()).isEqualTo("node-cargo-wording");
        assertThat(effective.nodeVariants()).containsExactly(arm);
    }

    @Test
    void resolveVariant_controlArm_keepsBaseInstruction() {
        // Variant with a blank override = the control arm: keep base wording.
        NodeVariant control = new NodeVariant("control", 1, "  ");
        ChatFlowNode base = node("ai-cargo", "Base wording.", "exp", List.of(control));
        ChatFlowNode effective = base.resolveVariant("conv-1");
        assertThat(effective).isSameAs(base);
        assertThat(effective.aiInstruction()).isEqualTo("Base wording.");
    }

    @Test
    void resolveVariant_swapsTemplate_onFunctionCallNode() {
        // T72 extension — the swap is type-agnostic: on a functionCall node the
        // aiInstruction IS the tool input JSON template, so the same mechanism
        // A/Bs the tool's input without duplicating the flow.
        NodeVariant arm = new NodeVariant("v2-template", 1, "{\"cargo\":\"{{cargo}}\",\"v\":2}");
        NodeData data = new NodeData(
                "Compose proposal", "functionCall", "{\"cargo\":\"{{cargo}}\"}",
                null, null, "compor_proposta", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                "tool-input-wording", List.of(arm));
        ChatFlowNode base = new ChatFlowNode("fn-proposta", "functionCall", data);

        ChatFlowNode effective = base.resolveVariant("conv-1");

        assertThat(effective.type()).isEqualTo("functionCall");
        assertThat(effective.functionName()).isEqualTo("compor_proposta");
        assertThat(effective.aiInstruction()).isEqualTo("{\"cargo\":\"{{cargo}}\",\"v\":2}");
    }

    @Test
    void formatNodeVariantTrace_emptyCases() {
        assertThat(ChatFlowNode.formatNodeVariantTrace(null, "conv")).isEmpty();
        ChatFlowNode plain = node("n1", "q", null, null);
        assertThat(ChatFlowNode.formatNodeVariantTrace(plain, "conv")).isEmpty();
        ChatFlowNode blankKey = node("n1", "q", "  ", List.of(new NodeVariant("a", 1, "x")));
        assertThat(ChatFlowNode.formatNodeVariantTrace(blankKey, "conv")).isEmpty();
    }

    @Test
    void formatNodeVariantTrace_emitsCanonicalShape() {
        ChatFlowNode node = node("ai-cargo", "base",
                "node-cargo-wording", List.of(new NodeVariant("punchy", 1, "x")));
        assertThat(ChatFlowNode.formatNodeVariantTrace(node, "conv-1"))
                .contains("node-cargo-wording:punchy:ai-cargo");
    }

    @Test
    void formatNodeVariantTrace_unsetLabel_whenVariantLabelBlank() {
        ChatFlowNode node = node("ai-cargo", "base",
                "exp", List.of(new NodeVariant(null, 1, "x")));
        assertThat(ChatFlowNode.formatNodeVariantTrace(node, "conv-1"))
                .contains("exp:(unset):ai-cargo");
    }

    private static ChatFlowNode node(String id, String aiInstruction, String expKey,
            List<NodeVariant> variants) {
        NodeData data = new NodeData(
                "label", "aiQuestion", aiInstruction,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                expKey, variants);
        return new ChatFlowNode(id, "aiQuestion", data);
    }
}
