/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.readability.TurReadabilityScorer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Deterministic tests for the content-fit evaluator (Block AA / §XXVI.4). The
 * LLM is mocked through {@link TurLlmSummaryService}; the readability scorer is
 * the real (pure) component. We assert the grounding hard-fail (ungrounded
 * spans dropped), the score fusion, and graceful degradation when the LLM is
 * unavailable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaContentFitEvaluatorTest {

    @Spy
    private TurReadabilityScorer readabilityScorer = new TurReadabilityScorer();

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurPersonaContentFitEvaluator evaluator;

    private static final String SOURCE =
            "The quarterly report leverages synergistic paradigms. "
                    + "We had a good year and sold many products.";

    private TurPersona audiencePersona() {
        TurPersona persona = new TurPersona();
        persona.setId("p1");
        persona.setName("low-literacy-shopper");
        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(TurPersonaReadingLevel.ELEMENTARY);
        audience.setPrimaryLanguage("en");
        persona.setAudience(audience);
        return persona;
    }

    private TurPersonaSource source(String text) {
        TurPersonaSource s = new TurPersonaSource();
        s.setId("s1");
        s.setSourceName("Q-report");
        s.setCachedText(text);
        return s;
    }

    @Test
    void groundedMisfitKept_ungroundedDropped() {
        String verdict = """
                {"fitScore": 40, "summary": "Too formal in places.",
                 "fits": ["Plain sentence about a good year"],
                 "misfits": [
                   {"span": "synergistic paradigms", "reason": "jargon", "suggestion": "working together"},
                   {"span": "this phrase is not in the text", "reason": "tone", "suggestion": "x"}
                 ]}
                """;
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, verdict, true));

        TurContentFitResult result = evaluator.evaluate(audiencePersona(), source(SOURCE), false);

        assertThat(result.llmUsed()).isTrue();
        // Ungrounded span dropped; only the verbatim one survives.
        assertThat(result.misfits()).hasSize(1);
        assertThat(result.misfits().getFirst().span()).isEqualTo("synergistic paradigms");
        assertThat(result.fits()).containsExactly("Plain sentence about a good year");
        assertThat(result.fitScore()).isBetween(0.0, 100.0);
    }

    @Test
    void fusesReadabilityWithLlmScore() {
        // LLM returns a high fit (90); readability for elementary on this jargon
        // text is lower — the fused score must sit strictly between them.
        String verdict = """
                {"fitScore": 90, "summary": "ok", "fits": [], "misfits": []}
                """;
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, verdict, true));

        TurContentFitResult result = evaluator.evaluate(audiencePersona(), source(SOURCE), false);

        assertThat(result.fitScore())
                .isGreaterThan(result.readabilityScore())
                .isLessThan(90.0);
    }

    @Test
    void degradesToReadabilityWhenLlmUnavailable() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(false, "No default LLM configured.", null, false));

        TurContentFitResult result = evaluator.evaluate(audiencePersona(), source(SOURCE), false);

        assertThat(result.llmUsed()).isFalse();
        assertThat(result.fitScore()).isEqualTo(result.readabilityScore());
        assertThat(result.error()).contains("No default LLM");
    }

    @Test
    void unparseableVerdictDegradesGracefully() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "sorry, I cannot do that", true));

        TurContentFitResult result = evaluator.evaluate(audiencePersona(), source(SOURCE), false);

        assertThat(result.llmUsed()).isFalse();
        assertThat(result.fitScore()).isEqualTo(result.readabilityScore());
    }

    @Test
    void blankSourceShortCircuitsWithoutCallingLlm() {
        TurContentFitResult result = evaluator.evaluate(audiencePersona(), source("  "), false);

        assertThat(result.llmUsed()).isFalse();
        assertThat(result.error()).contains("no extracted text");
    }
}
