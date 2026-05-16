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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;

/**
 * Pure-function tests for the helpers that back the new {@code writeSlot}
 * and {@code formCapture} chat-flow node types: variable interpolation,
 * question-like type classification, and the BR-document regex patterns
 * added to {@code VALIDATION_PATTERNS}.
 *
 * <p>No Spring context, no state repository — exercises only the static
 * methods so the suite stays fast (under 100ms total).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class ChatFlowOpsTest {

    // ─────────────────────────── interpolateVariables ───────────────────────────

    @Test
    void interpolate_replacesSingleVariable() {
        assertThat(ChatFlowOps.interpolateVariables("Olá, {{name}}!", Map.of("name", "Marina")))
                .isEqualTo("Olá, Marina!");
    }

    @Test
    void interpolate_replacesMultipleVariables() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Marina");
        vars.put("role", "CFO");
        assertThat(ChatFlowOps.interpolateVariables("{{name}} é {{role}}", vars))
                .isEqualTo("Marina é CFO");
    }

    @Test
    void interpolate_unknownVariableResolvesToEmpty() {
        // Forgiving contract — missing slots don't crash the flow.
        assertThat(ChatFlowOps.interpolateVariables("Olá, {{missing}}!", Map.of()))
                .isEqualTo("Olá, !");
    }

    @Test
    void interpolate_blankAndNullVariableMap() {
        assertThat(ChatFlowOps.interpolateVariables("static text", null)).isEqualTo("static text");
        assertThat(ChatFlowOps.interpolateVariables("", Map.of("a", "b"))).isEmpty();
        assertThat(ChatFlowOps.interpolateVariables(null, Map.of("a", "b"))).isEmpty();
    }

    @Test
    void interpolate_handlesWhitespaceAroundName() {
        assertThat(ChatFlowOps.interpolateVariables("Hi {{  name  }}!", Map.of("name", "Marina")))
                .isEqualTo("Hi Marina!");
    }

    @Test
    void interpolate_doesNotRecurseIntoReplacement() {
        // Replacement values containing {{...}} are NOT re-interpolated.
        // Single-pass keeps semantics predictable; authors chain writeSlot
        // nodes for composition.
        assertThat(ChatFlowOps.interpolateVariables("{{a}}", Map.of("a", "{{b}}", "b", "won't appear")))
                .isEqualTo("{{b}}");
    }

    @Test
    void interpolate_dollarSignsInValueAreLiterals() {
        // Matcher.quoteReplacement protects against $-driven group refs.
        assertThat(ChatFlowOps.interpolateVariables("Price: {{p}}", Map.of("p", "R$ 18.900")))
                .isEqualTo("Price: R$ 18.900");
    }

    // ─────────────────────────── isQuestionLike ───────────────────────────

    @Test
    void isQuestionLike_recognizesAiQuestion() {
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("aiQuestion"))).isTrue();
    }

    @Test
    void isQuestionLike_recognizesFormCapture() {
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("formCapture"))).isTrue();
    }

    @Test
    void isQuestionLike_rejectsOtherTypes() {
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("slot"))).isFalse();
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("writeSlot"))).isFalse();
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("switch"))).isFalse();
        assertThat(ChatFlowOps.isQuestionLike(nodeOfType("end"))).isFalse();
        assertThat(ChatFlowOps.isQuestionLike(null)).isFalse();
    }

    // ─────────────────────────── extractValue (validation patterns) ───────────────────────────
    //
    // The new patterns (cpf / cnpj / cep) match canonical Brazilian doc
    // formats. We do NOT yet run mod-11 digit checks — these tests pin the
    // current behavior so a future digit-check upgrade can land as a
    // separate, observable change.

    @Test
    void extractValue_cpfMatchesFormattedAndUnformatted() {
        assertThat(ChatFlowOps.extractValue("cpf", "Meu CPF é 123.456.789-09"))
                .isEqualTo("123.456.789-09");
        assertThat(ChatFlowOps.extractValue("cpf", "12345678909"))
                .isEqualTo("12345678909");
    }

    @Test
    void extractValue_cnpjMatchesFormattedAndUnformatted() {
        assertThat(ChatFlowOps.extractValue("cnpj", "CNPJ 12.345.678/0001-95"))
                .isEqualTo("12.345.678/0001-95");
        assertThat(ChatFlowOps.extractValue("cnpj", "12345678000195"))
                .isEqualTo("12345678000195");
    }

    @Test
    void extractValue_cepMatchesFormattedAndUnformatted() {
        assertThat(ChatFlowOps.extractValue("cep", "04546-042")).isEqualTo("04546-042");
        assertThat(ChatFlowOps.extractValue("cep", "04546042")).isEqualTo("04546042");
    }

    @Test
    void extractValue_emailStillWorksAfterCpfCnpjAddition() {
        // Regression: re-keying the patterns map from Map.of to Map.ofEntries
        // shouldn't change the existing entries' behavior.
        assertThat(ChatFlowOps.extractValue("email", "Me mande em foo@bar.com por favor"))
                .isEqualTo("foo@bar.com");
    }

    @Test
    void extractValue_invalidCpfReturnsNull() {
        // Too few digits — no match.
        assertThat(ChatFlowOps.extractValue("cpf", "1234")).isNull();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static ChatFlowNode nodeOfType(String type) {
        return new ChatFlowNode("test-id", type, new NodeData(
                "label", type, null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, null, null, null));
    }
}
