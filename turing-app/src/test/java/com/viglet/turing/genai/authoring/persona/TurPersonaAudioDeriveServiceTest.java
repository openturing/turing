/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService.DraftResult;
import com.viglet.turing.genai.transcription.TurTranscriptionResult;
import com.viglet.turing.genai.transcription.TurTranscriptionService;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Deterministic tests for persona-from-audio derivation (Block AA / §XXVI.7).
 * Transcription + LLM are mocked. Asserts the transcribe→analyse→draft chain,
 * safe enum parsing, the BOTH default, and that the draft is never assigned an
 * id (so it cannot be mistaken for a saved persona).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaAudioDeriveServiceTest {

    @Mock
    private TurTranscriptionService transcriptionService;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurPersonaAudioDeriveService service;

    @Test
    void failsWhenTranscriptionFails() {
        when(transcriptionService.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.fail("no STT"));

        DraftResult result = service.derive(new byte[] { 1, 2 }, "audio/mpeg", "en");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("no STT");
        assertThat(result.draft()).isNull();
    }

    @Test
    void drafsPersonaFromTranscript() {
        when(transcriptionService.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("Olá, eu vendo carros há vinte anos.", "pt"));
        // Call 1 — classification JSON (no systemInstruction here in T716).
        String classifyJson = """
                {"name": "veteran-car-salesman", "description": "Seasoned dealer.",
                 "tone": "CASUAL", "verbosity": 4, "languageStyle": "PERSUASIVE",
                 "personaKind": "BOTH", "readingLevel": "SECONDARY",
                 "domainExpertise": "EXPERT", "vocabularyCeiling": "everyday words",
                 "primaryLanguage": "pt",
                 "openness": 70, "conscientiousness": 55, "extraversion": 120,
                 "neuroticism": 30}
                """;
        when(llmSummaryService.generate(startsWith("persona-from-audio:classify"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, classifyJson, true));
        // Call 2 — the rich markdown voice brief becomes the systemInstruction.
        String brief = "# Quem você é\nVocê é um vendedor veterano. Fala \"né?\" o tempo todo.";
        when(llmSummaryService.generate(startsWith("persona-from-audio:brief"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, brief, true));

        DraftResult result = service.derive(new byte[] { 1 }, "audio/mpeg", "pt");

        assertThat(result.success()).isTrue();
        assertThat(result.transcript()).contains("vendo carros");
        assertThat(result.draft()).isNotNull();
        assertThat(result.draft().getId()).isNull();
        assertThat(result.draft().getName()).isEqualTo("veteran-car-salesman");
        assertThat(result.draft().getSystemInstruction()).isEqualTo(brief);
        assertThat(result.draft().getTone()).isEqualTo(TurPersonaTone.CASUAL);
        assertThat(result.draft().getVerbosity()).isEqualTo(4);
        assertThat(result.draft().getPersonaKind()).isEqualTo(TurPersonaKind.BOTH);
        assertThat(result.draft().getAudience()).isNotNull();
        assertThat(result.draft().getAudience().getReadingLevel())
                .isEqualTo(TurPersonaReadingLevel.SECONDARY);
        assertThat(result.draft().getAudience().getPrimaryLanguage()).isEqualTo("pt");
        // T717 — Big Five: scored traits carry over, out-of-range clamps to 0-100,
        // and a trait the model omitted stays null ("unset").
        assertThat(result.draft().getOpenness()).isEqualTo(70);
        assertThat(result.draft().getConscientiousness()).isEqualTo(55);
        assertThat(result.draft().getExtraversion()).isEqualTo(100); // clamped from 120
        assertThat(result.draft().getNeuroticism()).isEqualTo(30);
        assertThat(result.draft().getAgreeableness()).isNull(); // omitted → unset
    }

    @Test
    void synthesisesNameAndSystemInstructionWhenModelLeavesThemBlank() {
        when(transcriptionService.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("A calm expert explains taxes.", "en"));
        // Classification omits name; the brief call comes back blank.
        String classifyJson = """
                {"description": "A calm tax expert.",
                 "tone": "TECHNICAL", "verbosity": 2, "languageStyle": "INSTRUCTIONAL",
                 "personaKind": "BOTH", "domainExpertise": "EXPERT"}
                """;
        when(llmSummaryService.generate(startsWith("persona-from-audio:classify"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, classifyJson, false));
        when(llmSummaryService.generate(startsWith("persona-from-audio:brief"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "", false));

        DraftResult result = service.derive(new byte[] { 1 }, "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        // name derived from the description slug — never blank / placeholder.
        assertThat(result.draft().getName()).isEqualTo("a-calm-tax-expert");
        // systemInstruction synthesised from the extracted traits — never blank.
        assertThat(result.draft().getSystemInstruction())
                .isNotBlank()
                .contains("technical")
                .contains("instructional");
    }

    @Test
    void defaultsBothKindAndIgnoresBadEnums() {
        when(transcriptionService.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("Some words.", "en"));
        String classifyJson = """
                {"name": "x", "tone": "NONSENSE", "verbosity": 99,
                 "personaKind": "", "readingLevel": "BOGUS"}
                """;
        when(llmSummaryService.generate(startsWith("persona-from-audio:classify"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, classifyJson, false));
        when(llmSummaryService.generate(startsWith("persona-from-audio:brief"),
                anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "# Voice\nSpeaks plainly.", false));

        DraftResult result = service.derive(new byte[] { 1 }, "audio/wav", null);

        assertThat(result.success()).isTrue();
        assertThat(result.draft().getTone()).isNull();
        assertThat(result.draft().getVerbosity()).isEqualTo(5); // clamped 1..5
        assertThat(result.draft().getPersonaKind()).isEqualTo(TurPersonaKind.BOTH);
        assertThat(result.draft().getAudience().getReadingLevel()).isNull();
        // No OCEAN scores in the JSON → all traits stay null (unset).
        assertThat(result.draft().getOpenness()).isNull();
        assertThat(result.draft().getNeuroticism()).isNull();
    }
}
