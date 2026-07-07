/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.readability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;

/**
 * Deterministic tests for the pure readability scorer (Block AA / §XXVI.3).
 * No LLM, no Spring context — the scorer is the regression core, so we assert
 * the audience-relative fit signal: simple text fits a low-education reader,
 * dense jargon does not, and the same dense text scores higher for a graduate
 * reader.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurReadabilityScorerTest {

    private final TurReadabilityScorer scorer = new TurReadabilityScorer();

    private TurPersonaAudience audience(TurPersonaReadingLevel level, String lang) {
        TurPersonaAudience a = new TurPersonaAudience();
        a.setReadingLevel(level);
        a.setPrimaryLanguage(lang);
        return a;
    }

    @Test
    void blankTextIsNeutral() {
        TurReadabilityResult result = scorer.score("   ",
                audience(TurPersonaReadingLevel.SECONDARY, "en"));
        assertThat(result.fitScore()).isEqualTo(50.0);
        assertThat(result.metrics().wordCount()).isZero();
    }

    @Test
    void simpleTextFitsLowEducationReader() {
        String simple = "The cat sat on the mat. The dog ran fast. "
                + "We had fun in the sun. It was a good day.";
        TurReadabilityResult result = scorer.score(simple,
                audience(TurPersonaReadingLevel.ELEMENTARY, "en"));
        assertThat(result.fitScore()).isGreaterThan(80.0);
        assertThat(result.metrics().fleschKincaidGrade())
                .isLessThan(TurPersonaReadingLevel.ELEMENTARY.getApproxGradeCeiling());
    }

    @Test
    void denseJargonMisfitsLowEducationButFitsGraduate() {
        String dense = "The aforementioned epistemological framework necessitates "
                + "a comprehensive reconceptualization of the underlying "
                + "methodological presuppositions inherent in contemporary "
                + "interdisciplinary investigations of socioeconomic stratification.";
        TurReadabilityResult low = scorer.score(dense,
                audience(TurPersonaReadingLevel.ELEMENTARY, "en"));
        TurReadabilityResult high = scorer.score(dense,
                audience(TurPersonaReadingLevel.GRADUATE, "en"));

        assertThat(low.fitScore()).isLessThan(40.0);
        assertThat(high.fitScore()).isGreaterThan(low.fitScore());
        assertThat(low.metrics().complexWordRatio()).isGreaterThan(0.2);
    }

    @Test
    void vocabularyCeilingAllowListReducesComplexWordCount() {
        String text = "Photosynthesis converts sunlight into energy. "
                + "Photosynthesis is fundamental.";
        TurPersonaAudience noAllow = audience(TurPersonaReadingLevel.MIDDLE, "en");
        TurPersonaAudience withAllow = audience(TurPersonaReadingLevel.MIDDLE, "en");
        withAllow.setVocabularyCeiling("photosynthesis|fundamental");

        double ratioNoAllow = scorer.score(text, noAllow).metrics().complexWordRatio();
        double ratioWithAllow = scorer.score(text, withAllow).metrics().complexWordRatio();

        assertThat(ratioWithAllow).isLessThan(ratioNoAllow);
    }

    @Test
    void passiveVoiceIsDetected() {
        String passive = "The report was written by the team. "
                + "The results were analyzed carefully. "
                + "The conclusions were reviewed by experts.";
        TurReadabilityResult result = scorer.score(passive,
                audience(TurPersonaReadingLevel.SECONDARY, "en"));
        assertThat(result.metrics().passiveVoiceRatio()).isGreaterThan(0.5);
    }

    @Test
    void portugueseUsesAdaptedCoefficients() {
        String pt = "O gato dormiu no sofá. O menino correu no parque. "
                + "Hoje o dia está bonito e quente.";
        TurReadabilityResult result = scorer.score(pt,
                audience(TurPersonaReadingLevel.ELEMENTARY, "pt"));
        // Simple PT prose should land comfortably for a low-education reader.
        assertThat(result.fitScore()).isGreaterThan(70.0);
        assertThat(result.metrics().wordCount()).isGreaterThan(0);
    }
}
