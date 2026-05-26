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
    // Two layers per BR document validation: (1) regex matches the format
    // (digits + optional canonical punctuation), (2) mod-11 checksum verifies
    // the digits are mathematically valid. Numbers like 12345678900 satisfy
    // the regex but fail step 2 — extractValue returns null so the strategy
    // re-asks just like any other validation miss.

    @Test
    void extractValue_cpfMatchesFormattedAndUnformatted() {
        // 123.456.789-09 is a mathematically valid CPF (verified by hand:
        // sum = 210, rem=1 → DV1=0 ✓; sum = 255, rem=2 → DV2=9 ✓).
        assertThat(ChatFlowOps.extractValue("cpf", "Meu CPF é 123.456.789-09"))
                .isEqualTo("123.456.789-09");
        assertThat(ChatFlowOps.extractValue("cpf", "12345678909"))
                .isEqualTo("12345678909");
    }

    @Test
    void extractValue_cpfChecksumRejectsInvalidNumber() {
        // 12345678900 matches the regex but the last digit (0) is wrong —
        // the actual check digit for 12345678 is 9 + 0 = 90 (DV1=0, DV2=9).
        // Anything else → null (treat as no match → strategy re-asks).
        assertThat(ChatFlowOps.extractValue("cpf", "Meu CPF é 12345678900"))
                .as("invalid checksum → null even though regex matches")
                .isNull();
    }

    @Test
    void extractValue_cpfRejectsRepeatedDigits() {
        // 11111111111 mathematically satisfies mod-11 (all weights cancel
        // to 0 mod 11) but Receita Federal flags these as invalid. Same
        // for 000.000.000-00, 222.222.222-22, etc.
        assertThat(ChatFlowOps.extractValue("cpf", "11111111111")).isNull();
        assertThat(ChatFlowOps.extractValue("cpf", "000.000.000-00")).isNull();
        assertThat(ChatFlowOps.extractValue("cpf", "999.999.999-99")).isNull();
    }

    @Test
    void extractValue_cnpjMatchesFormattedAndUnformatted() {
        // 12.345.678/0001-95 is a mathematically valid CNPJ (verified by
        // hand: sum1=222 → DV1=9 ✓; sum2=237 → DV2=5 ✓).
        assertThat(ChatFlowOps.extractValue("cnpj", "CNPJ 12.345.678/0001-95"))
                .isEqualTo("12.345.678/0001-95");
        assertThat(ChatFlowOps.extractValue("cnpj", "12345678000195"))
                .isEqualTo("12345678000195");
    }

    @Test
    void extractValue_cnpjChecksumRejectsInvalidNumber() {
        // Flip the last digit to 0 → fails DV2 → null.
        assertThat(ChatFlowOps.extractValue("cnpj", "12.345.678/0001-90"))
                .isNull();
    }

    @Test
    void extractValue_cnpjRejectsRepeatedDigits() {
        assertThat(ChatFlowOps.extractValue("cnpj", "11.111.111/1111-11")).isNull();
        assertThat(ChatFlowOps.extractValue("cnpj", "00000000000000")).isNull();
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

    // ─────────────────────────── T23 canonicalizeAgainstInlineOptions ───────────────────────────

    @Test
    void canonicalize_exactMatch_returnsCanonicalCasing() {
        List<String> options = List.of("RH/L&D", "Gestor", "C-level", "Comprador");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("gestor", options))
                .isEqualTo("Gestor");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("COMPRADOR", options))
                .isEqualTo("Comprador");
    }

    @Test
    void canonicalize_diacriticTypo_matchesViaEditDistance1() {
        // The §IV.6 headline case: user typed "Senior" (no circumflex),
        // option is "Sênior" — 1 edit (ê → e). Must canonicalize.
        List<String> options = List.of("Gerente Sênior", "Diretor", "Estagiário");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("Gerente Senior", options))
                .isEqualTo("Gerente Sênior");
    }

    @Test
    void canonicalize_singleCharTypo_matchesViaEditDistance1() {
        List<String> options = List.of("Comprador", "Gestor", "C-level");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("Compradoor", options))
                .isEqualTo("Comprador");
    }

    @Test
    void canonicalize_doubleTypo_matchesViaEditDistance2() {
        // Two char errors but Levenshtein 2 still catches it. Beyond that,
        // we'd risk false positives so canonicalization stops.
        List<String> options = List.of("Gerente Sênior", "Diretor");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("Gerente Senir", options))
                .isEqualTo("Gerente Sênior");
    }

    @Test
    void canonicalize_prefixNoise_matchesViaSubstring() {
        // The "Sr Comprador" → "Comprador" case — Levenshtein 2 isn't
        // enough (3 chars to delete: "S", "r", " "), but substring
        // containment catches it deterministically.
        List<String> options = List.of("Comprador", "Gestor", "C-level");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("Sr Comprador", options))
                .isEqualTo("Comprador");
    }

    @Test
    void canonicalize_substringMatch_prefersLongerOption() {
        // "Gerente Senior" contains both "Gerente Senior" itself AND
        // "Senior" — prefer the longer, more specific label.
        List<String> options = List.of("Senior", "Gerente Senior", "Junior");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("sou um Gerente Senior aqui", options))
                .isEqualTo("Gerente Senior");
    }

    @Test
    void canonicalize_noMatch_returnsOriginalValue() {
        List<String> options = List.of("Sim", "Não", "Talvez");
        // Three-edit gap that's also not a substring — passes through.
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("computador", options))
                .isEqualTo("computador");
    }

    @Test
    void canonicalize_emptyOrNullInputs_passThrough() {
        List<String> options = List.of("A", "B");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions(null, options)).isNull();
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("", options)).isEmpty();
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("  ", options)).isEqualTo("  ");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("any", null)).isEqualTo("any");
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("any", List.of())).isEqualTo("any");
    }

    @Test
    void canonicalize_caseInsensitiveExact_doesNotEscalateToFuzzy() {
        // Sanity check on the cascade order: an exact case-insensitive
        // match takes precedence over fuzzy matches against other options
        // that share characters. Validates the early return in matchExact.
        List<String> options = List.of("Sim", "Sin", "Sí");
        // "sim" exactly matches "Sim" (case-fold). Don't bounce to "Sin"
        // or "Sí" via fuzzy — return "Sim" verbatim.
        assertThat(ChatFlowOps.canonicalizeAgainstInlineOptions("sim", options))
                .isEqualTo("Sim");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static ChatFlowNode nodeOfType(String type) {
        return new ChatFlowNode("test-id", type, new NodeData(
                "label", type, null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, null, null, null, null, null, null,
                null, null));
    }
}
