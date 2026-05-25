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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

/**
 * Tests for T31 / §IV.5 persona static-prompt cache. The unit tests below
 * cover the function-level contract of {@code composeStaticBlock(...)};
 * the {@code @Cacheable} memoization itself is exercised by the
 * integration tests that boot a Spring context (verified separately by
 * smoke ITs that come up green after the wiring).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurPersonaStaticPromptCacheTest {

    private TurPersonaStaticPromptCache cache;

    @BeforeEach
    void setUp() {
        cache = new TurPersonaStaticPromptCache();
    }

    @Test
    void returnsEmptyStringWhenPersonaIsNull() {
        assertThat(cache.composeStaticBlock(null)).isEmpty();
    }

    @Test
    void includesSystemInstructionWhenPresent() {
        TurPersona p = persona("p-1", "Be sympathetic and clear.");
        assertThat(cache.composeStaticBlock(p))
                .contains("Be sympathetic and clear.");
    }

    @Test
    void includesStyleGuidelinesWhenAnyFieldSet() {
        TurPersona p = persona("p-2", null);
        p.setTone(TurPersonaTone.FORMAL);
        p.setVerbosity(3);
        p.setLanguageStyle(TurPersonaLanguageStyle.NEUTRAL);
        String out = cache.composeStaticBlock(p);
        assertThat(out).contains("# Style Guidelines");
        assertThat(out).contains("Tone: formal");
        assertThat(out).contains("Verbosity (1=terse, 5=expansive): 3");
        assertThat(out).contains("Language Style: neutral");
    }

    @Test
    void skipsStyleGuidelinesWhenAllFieldsBlank() {
        TurPersona p = persona("p-3", "instr");
        // Entity default for verbosity is 3 — must override to 0
        // alongside null tone / style for the block to be skipped.
        p.setVerbosity(0);
        String out = cache.composeStaticBlock(p);
        assertThat(out).doesNotContain("# Style Guidelines");
    }

    @Test
    void includesRequiredVocabularyBlockWhenSet() {
        TurPersona p = persona("p-4", null);
        p.setMandatoryTerms("agile | iteration | sprint");
        String out = cache.composeStaticBlock(p);
        assertThat(out).contains("# Required Vocabulary");
        assertThat(out).contains("- agile");
        assertThat(out).contains("- iteration");
        assertThat(out).contains("- sprint");
    }

    @Test
    void includesForbiddenVocabularyBlockWhenSet() {
        TurPersona p = persona("p-5", null);
        p.setForbiddenTerms("guaranteed | risk-free");
        String out = cache.composeStaticBlock(p);
        assertThat(out).contains("# Forbidden Vocabulary");
        assertThat(out).contains("- guaranteed");
        assertThat(out).contains("- risk-free");
    }

    @Test
    void skipsTermsBlockWhenBlank() {
        TurPersona p = persona("p-6", "x");
        // mandatoryTerms = empty pipes only
        p.setMandatoryTerms("  |  ");
        p.setForbiddenTerms(null);
        String out = cache.composeStaticBlock(p);
        assertThat(out).doesNotContain("# Required Vocabulary");
        assertThat(out).doesNotContain("# Forbidden Vocabulary");
    }

    @Test
    void clampsVerbosityToValidRange() {
        TurPersona p = persona("p-7", null);
        p.setTone(TurPersonaTone.CASUAL);
        p.setVerbosity(99);
        assertThat(cache.composeStaticBlock(p)).contains("Verbosity (1=terse, 5=expansive): 5");
        p.setVerbosity(-3);
        // Clamps to 1 for negative
        assertThat(cache.composeStaticBlock(p)).contains("Verbosity (1=terse, 5=expansive): 1");
    }

    @Test
    void outputIsDeterministicForSamePersonaState() {
        TurPersona p = persona("p-8", "Be clear.");
        p.setTone(TurPersonaTone.CASUAL);
        p.setVerbosity(2);
        p.setMandatoryTerms("foo | bar");
        String first = cache.composeStaticBlock(p);
        String second = cache.composeStaticBlock(p);
        // Determinism (cache safety): same input → identical output
        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentPersonasYieldDifferentOutput() {
        TurPersona a = persona("p-9a", "Voice A");
        TurPersona b = persona("p-9b", "Voice B");
        assertThat(cache.composeStaticBlock(a))
                .isNotEqualTo(cache.composeStaticBlock(b));
    }

    @Test
    void doesNotIncludeFewShotMarker() {
        // Few-shot block is composed by TurPersonaPromptComposer, NOT by
        // the cache bean — pin the boundary so a future refactor doesn't
        // accidentally leak few-shot content (and its userQuery
        // dependency) into the cacheable surface.
        TurPersona p = persona("p-10", "x");
        assertThat(cache.composeStaticBlock(p))
                .doesNotContain("# Few-Shot Examples");
    }

    @Test
    void doesNotIncludeBaseSeparator() {
        // The "----" separator that joins the persona block to the agent
        // base prompt is appended by the composer, not the cache, so the
        // cached output is reusable across different basePrompts.
        TurPersona p = persona("p-11", "x");
        assertThat(cache.composeStaticBlock(p))
                .doesNotContain("\n\n----\n");
    }

    private static TurPersona persona(String id, String instruction) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName("Persona " + id);
        p.setSystemInstruction(instruction);
        return p;
    }
}
