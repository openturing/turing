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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@code ChatFlowOps.isValidCpfChecksum} and
 * {@code isValidCnpjChecksum} — Brazilian document checksum primitives that
 * sit on the LLM_JUDGE hot path: a buggy implementation either accepts
 * malformed docs (breaks downstream API calls) or rejects valid ones (the
 * visitor types it again and again — UX nightmare).
 *
 * <p>Lives in the {@code com.viglet.turing.genai.flow.strategy} package
 * so it can call the package-private static methods directly without
 * reflection. The originally-planned host was a {@code @Nested} class
 * inside {@code TurAgentChatExecutorContractIT}, but that IT is in
 * {@code com.viglet.turing.genai} which can't see package-private symbols
 * here — Step 3 of the §0 plan moved the skeleton over.
 *
 * <p>Pure JUnit: no Spring context, no mocks, no database. Suite runs in
 * &lt; 50 ms.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class ChatFlowOpsBrDocUnitTest {

    // ─────────────────────────── CPF ───────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "11144477735",       // canonical 11-digit, valid checksum
            "111.444.777-35",    // same value, formatted (mod-11 ignores punctuation)
            "390.533.447-05",    // second valid example, formatted
            "39053344705",       // same as above, unformatted
    })
    void isValidCpfChecksum_acceptsKnownValidNumbers(String cpf) {
        assertThat(ChatFlowOps.isValidCpfChecksum(cpf))
                .as("CPF '%s' must be accepted by mod-11", cpf)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11111111111",   // all-same-digit guard rejects this (mathematically passes mod-11)
            "00000000000",   // same
            "99999999999",   // same
            "33333333333",   // same
            "12345678900",   // 11 digits but wrong checksum
            "11144477734",   // canonical valid mutated by one digit → invalid
    })
    void isValidCpfChecksum_rejectsInvalidChecksums(String cpf) {
        assertThat(ChatFlowOps.isValidCpfChecksum(cpf))
                .as("CPF '%s' must be rejected (wrong checksum or repeated-digit guard)", cpf)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",                  // too short
            "123456789012",           // too long
            "abcdefghijk",            // letters
    })
    void isValidCpfChecksum_rejectsMalformed(String cpf) {
        assertThat(ChatFlowOps.isValidCpfChecksum(cpf))
                .as("CPF '%s' must be rejected — does not normalize to 11 digits", cpf)
                .isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isValidCpfChecksum_rejectsNullOrEmpty(String cpf) {
        assertThat(ChatFlowOps.isValidCpfChecksum(cpf)).isFalse();
    }

    // ─────────────────────────── CNPJ ───────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "11222333000181",       // canonical 14-digit, valid checksum
            "11.222.333/0001-81",   // same value, formatted (punctuation stripped)
    })
    void isValidCnpjChecksum_acceptsKnownValidNumbers(String cnpj) {
        assertThat(ChatFlowOps.isValidCnpjChecksum(cnpj))
                .as("CNPJ '%s' must be accepted by mod-11", cnpj)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11111111111111",   // all-same-digit guard
            "00000000000000",   // same
            "12345678901234",   // wrong checksum
            "11222333000180",   // canonical valid mutated → invalid
    })
    void isValidCnpjChecksum_rejectsInvalidChecksums(String cnpj) {
        assertThat(ChatFlowOps.isValidCnpjChecksum(cnpj))
                .as("CNPJ '%s' must be rejected (wrong checksum or repeated-digit guard)", cnpj)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "12345678901234567890",   // way too long
    })
    void isValidCnpjChecksum_rejectsMalformed(String cnpj) {
        assertThat(ChatFlowOps.isValidCnpjChecksum(cnpj))
                .as("CNPJ '%s' must be rejected — does not normalize to 14 digits", cnpj)
                .isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isValidCnpjChecksum_rejectsNullOrEmpty(String cnpj) {
        assertThat(ChatFlowOps.isValidCnpjChecksum(cnpj)).isFalse();
    }

    /**
     * Sanity guard: the two functions are not interchangeable. An 11-digit
     * input must NEVER pass the CNPJ check, and a 14-digit input must
     * NEVER pass the CPF check — even if their lengths line up by
     * coincidence after stripping punctuation.
     */
    @Test
    void crossLength_cpfStringRejectedByCnpjAndViceVersa() {
        assertThat(ChatFlowOps.isValidCnpjChecksum("11144477735"))
                .as("11-digit CPF must NOT pass CNPJ check (wrong length)").isFalse();
        assertThat(ChatFlowOps.isValidCpfChecksum("11222333000181"))
                .as("14-digit CNPJ must NOT pass CPF check (wrong length)").isFalse();
    }
}
