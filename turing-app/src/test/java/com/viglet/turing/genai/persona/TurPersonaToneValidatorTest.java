/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Unit tests for {@link TurPersonaToneValidator}. No Spring context — the
 * validator is a pure {@code @Component} with no dependencies that need
 * mocking. Pins the T22 / §IV.3 stem-matching contract across the three
 * supported analyzer locales (PT / EN / ES).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurPersonaToneValidatorTest {

    private final TurPersonaToneValidator validator = new TurPersonaToneValidator();

    // ── Pre-T22 baselines (must keep working) ──

    @Test
    void nullPersona_passesThroughUnchanged() {
        TurPersonaValidationResult r = validator.validate(null, "qualquer texto");
        assertThat(r.passed()).isTrue();
        assertThat(r.sanitizedText()).isEqualTo("qualquer texto");
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void nullResponseText_returnsEmptyPassed() {
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p, null);
        assertThat(r.passed()).isTrue();
        assertThat(r.sanitizedText()).isEmpty();
    }

    @Test
    void emptyForbiddenList_passesThroughUnchanged() {
        TurPersona p = persona("");
        TurPersonaValidationResult r = validator.validate(p, "qualquer texto");
        assertThat(r.passed()).isTrue();
        assertThat(r.sanitizedText()).isEqualTo("qualquer texto");
    }

    @Test
    void exactWordMatch_stillCaughtAndMasked() {
        // The legacy substring check found exact matches; T22 keeps that.
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p, "Nosso produto é competitivo.");
        assertThat(r.passed()).isFalse();
        assertThat(r.sanitizedText()).contains("[***]").doesNotContain("competitivo");
        assertThat(r.violations()).containsExactly("competitivo");
    }

    // ── T22 / §IV.3 morphology cases ──

    @Test
    void competitivo_matchesCompetitividade_inPortuguese() {
        // The headline §IV.3 case: forbidden "competitivo" must catch the
        // bot saying "competitividade" — same stem under PortugueseAnalyzer.
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p,
                "Nosso diferencial é a competitividade no mercado.");
        assertThat(r.passed()).isFalse();
        assertThat(r.violations()).containsExactly("competitivo");
        assertThat(r.sanitizedText())
                .doesNotContain("competitividade")
                .contains("[***]");
    }

    @Test
    void competitivo_matchesCompetitiva_andCompetitivamente() {
        TurPersona p = persona("competitivo");

        TurPersonaValidationResult feminine = validator.validate(p,
                "A oferta é competitiva.");
        assertThat(feminine.passed()).isFalse();
        assertThat(feminine.sanitizedText()).contains("[***]").doesNotContain("competitiva");

        TurPersona p2 = persona("competitivo");
        TurPersonaValidationResult adverb = validator.validate(p2,
                "Precificamos competitivamente.");
        assertThat(adverb.passed()).isFalse();
        assertThat(adverb.sanitizedText()).contains("[***]").doesNotContain("competitivamente");
    }

    @Test
    void englishStemming_runMatchesRunning_andRanIfPorter() {
        // Porter (EnglishAnalyzer) stems "running" → "run" and "runs" → "run".
        // The forbidden entry "running" therefore catches any morphological
        // variant the bot emits.
        TurPersona p = persona("running");
        TurPersonaValidationResult r = validator.validate(p,
                "I have been runs and runs in the park.");
        assertThat(r.passed()).isFalse();
        // Both "runs" tokens get masked.
        assertThat(r.sanitizedText()).contains("[***]").doesNotContain("runs");
    }

    @Test
    void spanishStemming_corredorCatchesCorredores() {
        // SpanishAnalyzer stems "corredor"/"corredores" identically.
        TurPersona p = persona("corredor");
        TurPersonaValidationResult r = validator.validate(p,
                "Los corredores ganaron la carrera.");
        assertThat(r.passed()).isFalse();
        assertThat(r.sanitizedText()).contains("[***]").doesNotContain("corredores");
    }

    @Test
    void crossLanguageStem_ptCarroCatchesEsCarro() {
        // PT "carro" and ES "carro" stem to the same token under both
        // analyzers — bilingual persona protected with one forbidden entry.
        TurPersona p = persona("carro");
        TurPersonaValidationResult ptHit = validator.validate(p, "O carro é vermelho.");
        assertThat(ptHit.passed()).isFalse();

        TurPersonaValidationResult esHit = validator.validate(p, "El carro es rojo.");
        assertThat(esHit.passed()).isFalse();
    }

    // ── Negative cases ──

    @Test
    void unrelatedReply_doesNotMatch() {
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p,
                "Olá, como posso ajudar você hoje?");
        assertThat(r.passed()).isTrue();
        assertThat(r.sanitizedText()).isEqualTo("Olá, como posso ajudar você hoje?");
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void stopwordOnlyTerm_doesNotMatchEverything() {
        // Forbidden term "de para com" tokenizes to nothing under PT (all
        // stopwords). It MUST NOT match every reply — that would gut the
        // sanitizer. T22 skips such terms with a debug log.
        TurPersona p = persona("de para com");
        TurPersonaValidationResult r = validator.validate(p, "qualquer coisa aqui");
        assertThat(r.passed()).isTrue();
        assertThat(r.violations()).isEmpty();
    }

    // ── Multi-term + multi-hit + masking ──

    @Test
    void multipleForbiddenTerms_allCaughtIndependently() {
        TurPersona p = persona("competitivo|preço");
        TurPersonaValidationResult r = validator.validate(p,
                "Nosso preço é competitivo no mercado.");
        assertThat(r.passed()).isFalse();
        assertThat(r.violations()).containsExactlyInAnyOrder("competitivo", "preço");
        assertThat(r.sanitizedText()).doesNotContain("preço").doesNotContain("competitivo");
    }

    @Test
    void overlappingAnalyzerMatches_produceSingleMaskNotMultiple() {
        // "competitivo" is recognized by both PT and ES stemmers at the
        // same character offsets. The merge step in applyMasks should
        // collapse them so the user sees ONE [***], not "[***][***]".
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p, "competitivo");
        assertThat(r.passed()).isFalse();
        assertThat(r.sanitizedText()).isEqualTo("[***]");
    }

    @Test
    void multipleHitsInSameReply_allMasked() {
        TurPersona p = persona("competitivo");
        TurPersonaValidationResult r = validator.validate(p,
                "Nosso plano é competitivo e a entrega é competitiva.");
        assertThat(r.passed()).isFalse();
        // Two original words → two [***] (not adjacent, no merging).
        long maskCount = r.sanitizedText().chars().filter(c -> c == '[').count();
        assertThat(maskCount).isEqualTo(2L);
        assertThat(r.sanitizedText()).doesNotContain("competitivo").doesNotContain("competitiva");
    }

    /** Helper — builds a TurPersona with the given forbidden-term spec. */
    private static TurPersona persona(String forbiddenTerms) {
        TurPersona p = new TurPersona();
        p.setName("Test Persona");
        p.setForbiddenTerms(forbiddenTerms);
        return p;
    }
}
