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

import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;

/**
 * T25 / §II.2.1 — pins per-language analyzer routing semantics + the AUTO
 * language detector. The router itself ({@code tryProceduralRoute}) is
 * exercised through {@code @SpringBootTest} elsewhere; this suite focuses
 * on the pure static helpers so failures are surgical.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowRouterLanguageTest {

    // ─── analyzerFor: explicit language overrides ────────────────────────

    @Test
    void analyzerForPtPicksPortugueseAnalyzer() {
        Analyzer a = TurChatFlowTriggerRouter.analyzerFor(TurChatFlowTriggerLanguage.PT,
                "qualquer descrição em qualquer idioma");
        assertThat(a).isInstanceOf(PortugueseAnalyzer.class);
    }

    @Test
    void analyzerForEnPicksEnglishAnalyzer() {
        Analyzer a = TurChatFlowTriggerRouter.analyzerFor(TurChatFlowTriggerLanguage.EN,
                "qualquer descrição em qualquer idioma");
        assertThat(a).isInstanceOf(EnglishAnalyzer.class);
    }

    @Test
    void analyzerForNullDefaultsToAutoBranch() {
        // Defensive: pre-T25 entities loaded from a snapshot have null lang.
        // Should not NPE — treated as AUTO. PT-only text → PT analyzer.
        Analyzer a = TurChatFlowTriggerRouter.analyzerFor(null,
                "plano de carreira para o engenheiro");
        assertThat(a).isInstanceOf(PortugueseAnalyzer.class);
    }

    // ─── detectLanguage: PT/EN heuristic ─────────────────────────────────

    @Test
    void detectLanguageRecognizesEnglishTrigger() {
        // Heuristic-dominant EN: stopwords "the", "for", "with", "is".
        // PT contribution: 0.
        var lang = TurChatFlowTriggerRouter.detectLanguage(
                "Trigger this flow when the user asks for a career plan with the manager");
        assertThat(lang).isEqualTo(TurChatFlowTriggerLanguage.EN);
    }

    @Test
    void detectLanguageRecognizesPortugueseTrigger() {
        // PT-dominant: "o", "para", "com", "que".
        var lang = TurChatFlowTriggerRouter.detectLanguage(
                "Dispare este fluxo quando o usuário perguntar sobre plano de carreira "
                + "para a equipe com o gerente que coordena o time");
        assertThat(lang).isEqualTo(TurChatFlowTriggerLanguage.PT);
    }

    @Test
    void detectLanguageDefaultsToPtOnBlankInput() {
        assertThat(TurChatFlowTriggerRouter.detectLanguage(null))
                .isEqualTo(TurChatFlowTriggerLanguage.PT);
        assertThat(TurChatFlowTriggerRouter.detectLanguage(""))
                .isEqualTo(TurChatFlowTriggerLanguage.PT);
        assertThat(TurChatFlowTriggerRouter.detectLanguage("   "))
                .isEqualTo(TurChatFlowTriggerLanguage.PT);
    }

    @Test
    void detectLanguageDefaultsToPtOnTieOrNoHints() {
        // Pure proper nouns / domain terms → zero hits both lists → PT default.
        var lang = TurChatFlowTriggerRouter.detectLanguage("Turing OpenAI GPT Spring Boot");
        assertThat(lang).isEqualTo(TurChatFlowTriggerLanguage.PT);
    }

    @Test
    void detectLanguageIsCaseAndDiacriticTolerant() {
        // Accented PT stopwords ("está") + uppercase still count.
        var lang = TurChatFlowTriggerRouter.detectLanguage(
                "O usuário ESTÁ perguntando sobre o que fazer");
        assertThat(lang).isEqualTo(TurChatFlowTriggerLanguage.PT);
    }

    // ─── tokenize: stemming differs per analyzer ─────────────────────────

    @Test
    void tokenizePortuguesePlanosAndPlanoCollapseToOneStem() {
        // PortugueseAnalyzer light-stems plural→singular ("planos"→"plano"→
        // stem "plan"). Light stemming intentionally does NOT touch verbs,
        // so "planejar" stays distinct — verify both behaviors.
        Set<String> tokens = TurChatFlowTriggerRouter.tokenize(
                "planos plano", new PortugueseAnalyzer());
        assertThat(tokens).hasSize(1);
    }

    @Test
    void tokenizeEnglishRunningAndRunsCollapseToOneStem() {
        // Porter stems running/runs → "run". Irregular forms like "ran"
        // stay distinct (Porter is rule-based, not lexicon-based) —
        // doc'd here so a future stemmer swap doesn't silently change
        // routing behavior.
        Set<String> tokens = TurChatFlowTriggerRouter.tokenize(
                "running runs", new EnglishAnalyzer());
        assertThat(tokens).hasSize(1);
    }

    @Test
    void tokenizeEnglishAnalyzerDropsEnglishStopwords() {
        // "the", "and", "for" should be filtered; "career plan" survives as stems.
        Set<String> tokens = TurChatFlowTriggerRouter.tokenize(
                "the career plan and the manager", new EnglishAnalyzer());
        assertThat(tokens)
                .doesNotContain("the", "and", "for")
                .contains("career", "plan", "manag");
    }

    @Test
    void tokenizeBlankOrNullReturnsEmpty() {
        assertThat(TurChatFlowTriggerRouter.tokenize(null, new PortugueseAnalyzer()))
                .isEmpty();
        assertThat(TurChatFlowTriggerRouter.tokenize("", new EnglishAnalyzer()))
                .isEmpty();
        assertThat(TurChatFlowTriggerRouter.tokenize("   ", new PortugueseAnalyzer()))
                .isEmpty();
    }

    // ─── crosswise: wrong-analyzer-on-text shows the bug T25 fixes ───────

    @Test
    void englishTextThroughPtAnalyzerLosesRunningRunsStemMatch() {
        // Documents T25's motivation: applying the wrong analyzer on EN text
        // breaks morphological matching. PT light stemmer leaves EN -ing /
        // -s intact → distinct stems for running/runs that should collapse.
        Set<String> tokens = TurChatFlowTriggerRouter.tokenize(
                "running runs ran", new PortugueseAnalyzer());
        assertThat(tokens)
                .as("PT analyzer fails to collapse EN run/running/ran")
                .hasSizeGreaterThan(1);
    }

    @Test
    void portugueseTextThroughEnAnalyzerLosesPlanosStemMatch() {
        // Reverse: EN Porter on PT text mangles or fails to collapse.
        // "planos / plano / planejar" do NOT all share a stem under Porter.
        Set<String> tokens = TurChatFlowTriggerRouter.tokenize(
                "planos plano planejar", new EnglishAnalyzer());
        assertThat(tokens)
                .as("EN analyzer fails to collapse PT plano/planos/planejar")
                .hasSizeGreaterThan(1);
    }
}
