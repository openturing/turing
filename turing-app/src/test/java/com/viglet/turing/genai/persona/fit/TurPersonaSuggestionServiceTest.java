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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Deterministic tests for the auto-suggest service (Block AA / §XXVI.8). The
 * underlying evaluator is mocked: each persona returns a stubbed
 * {@link TurContentFitResult}, and we assert the ranking order, rank numbering,
 * the {@code best*} head fields, and that only audience personas are graded.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaSuggestionServiceTest {

    @Mock
    private TurPersonaRepository personaRepository;

    @Mock
    private TurPersonaContentFitEvaluator evaluator;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurPersonaSuggestionService service;

    private static final String CONTENT = "Some content to place with an audience.";

    private TurPersona persona(String id, String name, TurPersonaKind kind, int enabled) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName(name);
        p.setPersonaKind(kind);
        p.setEnabled(enabled);
        if (kind != TurPersonaKind.SPEAKER) {
            p.setAudience(new TurPersonaAudience());
        }
        return p;
    }

    private TurContentFitResult resultWithScore(double score, boolean llmUsed) {
        return new TurContentFitResult(score, "summary", List.of(), List.of(),
                score, null, llmUsed, null, false, "suggest", "Suggested content");
    }

    @Test
    void ranksAudiencePersonasBestFitFirst() {
        TurPersona expert = persona("p-expert", "Expert", TurPersonaKind.AUDIENCE, 1);
        TurPersona novice = persona("p-novice", "Novice", TurPersonaKind.BOTH, 1);
        when(personaRepository.findAll()).thenReturn(List.of(expert, novice));
        when(evaluator.evaluateText(eq(expert), any(), any(), any(), anyBoolean()))
                .thenReturn(resultWithScore(35.0, true));
        when(evaluator.evaluateText(eq(novice), any(), any(), any(), anyBoolean()))
                .thenReturn(resultWithScore(88.0, true));
        when(llmSummaryService.isAvailable()).thenReturn(true);

        TurPersonaSuggestionReport report = service.suggest(CONTENT, null, false);

        assertThat(report.evaluatedCount()).isEqualTo(2);
        assertThat(report.bestPersonaId()).isEqualTo("p-novice");
        assertThat(report.bestScore()).isEqualTo(88.0);
        assertThat(report.llmAvailable()).isTrue();
        assertThat(report.rankings()).extracting(TurPersonaSuggestion::personaId)
                .containsExactly("p-novice", "p-expert");
        assertThat(report.rankings()).extracting(TurPersonaSuggestion::rank)
                .containsExactly(1, 2);
    }

    @Test
    void skipsSpeakerOnlyAndDisabledPersonas() {
        TurPersona speaker = persona("p-speaker", "Voice", TurPersonaKind.SPEAKER, 1);
        TurPersona disabled = persona("p-disabled", "Off", TurPersonaKind.AUDIENCE, 0);
        TurPersona audience = persona("p-audience", "Reader", TurPersonaKind.AUDIENCE, 1);
        when(personaRepository.findAll())
                .thenReturn(List.of(speaker, disabled, audience));
        when(evaluator.evaluateText(eq(audience), any(), any(), any(), anyBoolean()))
                .thenReturn(resultWithScore(60.0, true));
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);

        TurPersonaSuggestionReport report = service.suggest(CONTENT, null, false);

        assertThat(report.evaluatedCount()).isEqualTo(1);
        assertThat(report.bestPersonaId()).isEqualTo("p-audience");
        verify(evaluator, never()).evaluateText(eq(speaker), any(), any(), any(), anyBoolean());
        verify(evaluator, never()).evaluateText(eq(disabled), any(), any(), any(), anyBoolean());
    }

    @Test
    void explicitIdSubsetIncludesDisabledButStillRequiresAudienceFacet() {
        TurPersona speaker = persona("p-speaker", "Voice", TurPersonaKind.SPEAKER, 1);
        TurPersona disabled = persona("p-disabled", "Off", TurPersonaKind.AUDIENCE, 0);
        when(personaRepository.findAll()).thenReturn(List.of(speaker, disabled));
        when(evaluator.evaluateText(eq(disabled), any(), any(), any(), anyBoolean()))
                .thenReturn(resultWithScore(50.0, true));
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);

        TurPersonaSuggestionReport report =
                service.suggest(CONTENT, List.of("p-disabled", "p-speaker"), false);

        // Disabled audience persona is included when pinned explicitly; the
        // speaker-only persona is still rejected (no audience facet to grade).
        assertThat(report.evaluatedCount()).isEqualTo(1);
        assertThat(report.bestPersonaId()).isEqualTo("p-disabled");
        verify(evaluator, never()).evaluateText(eq(speaker), any(), any(), any(), anyBoolean());
    }

    @Test
    void emptyWhenNoAudiencePersonaExists() {
        when(personaRepository.findAll())
                .thenReturn(List.of(persona("p-speaker", "Voice", TurPersonaKind.SPEAKER, 1)));
        when(llmSummaryService.isAvailable()).thenReturn(false);

        TurPersonaSuggestionReport report = service.suggest(CONTENT, null, false);

        assertThat(report.evaluatedCount()).isZero();
        assertThat(report.bestPersonaId()).isNull();
        assertThat(report.bestScore()).isZero();
        assertThat(report.rankings()).isEmpty();
    }
}
