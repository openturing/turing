/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.genai.persona.fit.TurPersonaVerdictHealth;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto.FieldEdit;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto.Item;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchAdjustmentDto.Target;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaDomainExpertise;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchPersona;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.StructuredResult;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;
import com.viglet.turing.system.TurVerdictSource;

/**
 * T831 / §LXI.7 (Block BN) — a PERSONA suggestion may now carry a per-field diff
 * over the audience facet, and only what an operator could actually confirm
 * survives the grounding. The schema (T829) constrains the shape; this is what
 * constrains the meaning.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurPersonaMatchAdjustmentFieldEditTest {

    private static final String PROJECT = "p1";
    private static final String PERSONA = "a1";
    private static final String SOURCE = "s1";

    @Mock
    private TurPersonaMatchProjectRepository projectRepository;
    @Mock
    private TurPersonaMatchSourceRepository sourceRepository;
    @Mock
    private TurPersonaMatchPersonaRepository personaJoinRepository;
    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurPersonaMatchAnalysisService analysisService;
    @Mock
    private TurPersonaMatchHistoryService historyService;
    @Mock
    private TurLlmSummaryService llmSummaryService;
    @Mock
    private TurPersonaVerdictHealth verdictHealth;

    @InjectMocks
    private TurPersonaMatchAdjustmentService service;

    @BeforeEach
    void seedProject() {
        TurPersonaMatchProject project = new TurPersonaMatchProject();
        project.setId(PROJECT);
        project.setName("Portfolio");
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));

        TurPersonaMatchSource source = new TurPersonaMatchSource();
        source.setId(SOURCE);
        source.setSourceName("Whitepaper");
        when(sourceRepository.findByProject_IdOrderBySourceNameAsc(PROJECT))
                .thenReturn(List.of(source));

        TurPersonaMatchPersona join = new TurPersonaMatchPersona();
        join.setPersonaId(PERSONA);
        when(personaJoinRepository.findByProject_Id(PROJECT)).thenReturn(List.of(join));

        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(TurPersonaReadingLevel.GRADUATE);
        audience.setDomainExpertise(TurPersonaDomainExpertise.EXPERT);
        TurPersona persona = new TurPersona();
        persona.setId(PERSONA);
        persona.setName("Researcher");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        persona.setAudience(audience);
        when(personaRepository.findById(PERSONA)).thenReturn(Optional.of(persona));

        when(analysisService.getMatrix(PROJECT)).thenReturn(new TurPersonaMatchMatrixDto(
                PROJECT, List.of(new TurPersonaMatchCellDto(SOURCE, PERSONA, 41.0, 38.0, true,
                        "Too dense for this reader.", List.of(), List.of(), 55.0))));
        when(historyService.drift(PROJECT)).thenReturn(new TurPersonaMatchDriftDto(
                PROJECT, 1, null, null, 0, 41.0, 75.0, 0, 0, List.of()));
    }

    /** Feeds the parser one verdict, as if the provider had returned it. */
    private void modelAnswers(String json) {
        when(llmSummaryService.generateStructured(anyString(), anyString(), anyString(),
                anyBoolean(), any(), any()))
                .thenReturn(new StructuredResult(new SummaryResult(true, null, json, true),
                        TurVerdictSource.PROVIDER_SCHEMA));
    }

    private static String verdict(String fieldEdits) {
        return """
                {"items":[{"target":"PERSONA","targetId":"a1",
                "rationale":"Contents average 41%% fit.",
                "actions":["Lower the reading level."],
                "fieldEdits":%s}]}""".formatted(fieldEdits);
    }

    private Item onlyPersonaItem() {
        TurPersonaMatchAdjustmentDto dto = service.suggest(PROJECT, false);
        assertThat(dto.llmUsed()).as("the fixture verdict must parse").isTrue();
        return dto.items().stream().filter(i -> i.target() == Target.PERSONA)
                .findFirst().orElseThrow();
    }

    // ---- what survives -----------------------------------------------------

    @Test
    void keepsAnEditThatNamesARealFieldWithARealValue() {
        modelAnswers(verdict("""
                [{"field":"READING_LEVEL","value":"SECONDARY","reason":"Readers skim."}]"""));

        List<FieldEdit> edits = onlyPersonaItem().fieldEdits();

        assertThat(edits).singleElement().satisfies(edit -> {
            assertThat(edit.field()).isEqualTo("READING_LEVEL");
            // The diff's left-hand side comes from the persona, not from the model.
            assertThat(edit.currentValue()).isEqualTo("GRADUATE");
            assertThat(edit.proposedValue()).isEqualTo("SECONDARY");
            assertThat(edit.reason()).isEqualTo("Readers skim.");
            assertThat(edit.allowedValues()).contains("ELEMENTARY", "GRADUATE");
        });
    }

    @Test
    void acceptsTheFieldNameInWhateverCaseTheModelSpelledIt() {
        modelAnswers(verdict("""
                [{"field":"readingLevel","value":"MIDDLE","reason":"x"},
                 {"field":"domain-expertise","value":"NOVICE","reason":"y"}]"""));

        assertThat(onlyPersonaItem().fieldEdits())
                .extracting(FieldEdit::field)
                .containsExactly("READING_LEVEL", "DOMAIN_EXPERTISE");
    }

    @Test
    void carriesNoAllowedValuesForAFreeTextField() {
        modelAnswers(verdict("""
                [{"field":"VOCABULARY_CEILING","value":"plain language","reason":"x"}]"""));

        assertThat(onlyPersonaItem().fieldEdits()).singleElement().satisfies(edit -> {
            assertThat(edit.allowedValues()).isEmpty();
            assertThat(edit.currentValue()).isNull();
        });
    }

    // ---- what is dropped ---------------------------------------------------

    @Test
    void dropsAFieldThatIsNotWritable() {
        modelAnswers(verdict("""
                [{"field":"accessibilityNotes","value":"dyslexia-friendly","reason":"x"},
                 {"field":"name","value":"Skimmer","reason":"y"},
                 {"field":"primaryLanguage","value":"en","reason":"z"}]"""));

        assertThat(onlyPersonaItem().fieldEdits()).isEmpty();
    }

    @Test
    void dropsAValueThatIsNotOneOfTheEnumConstants() {
        modelAnswers(verdict("""
                [{"field":"READING_LEVEL","value":"POSTGRADUATE","reason":"x"}]"""));

        assertThat(onlyPersonaItem().fieldEdits()).isEmpty();
    }

    @Test
    void dropsAnEditProposingTheValueThePersonaAlreadyHas() {
        modelAnswers(verdict("""
                [{"field":"READING_LEVEL","value":"GRADUATE","reason":"x"},
                 {"field":"DOMAIN_EXPERTISE","value":"ADVANCED","reason":"y"}]"""));

        assertThat(onlyPersonaItem().fieldEdits())
                .extracting(FieldEdit::field)
                .containsExactly("DOMAIN_EXPERTISE");
    }

    @Test
    void keepsOnlyTheFirstEditWhenTheModelNamesAFieldTwice() {
        modelAnswers(verdict("""
                [{"field":"READING_LEVEL","value":"MIDDLE","reason":"first"},
                 {"field":"READING_LEVEL","value":"ELEMENTARY","reason":"second"}]"""));

        assertThat(onlyPersonaItem().fieldEdits()).singleElement()
                .satisfies(edit -> assertThat(edit.proposedValue()).isEqualTo("MIDDLE"));
    }

    @Test
    void neverPutsFieldEditsOnAContentItem() {
        modelAnswers("""
                {"items":[{"target":"CONTENT","targetId":"s1",
                "rationale":"Scores 41%.","actions":["Shorten the sentences."],
                "fieldEdits":[{"field":"READING_LEVEL","value":"MIDDLE","reason":"x"}]}]}""");

        TurPersonaMatchAdjustmentDto dto = service.suggest(PROJECT, false);

        assertThat(dto.items()).singleElement().satisfies(item -> {
            assertThat(item.target()).isEqualTo(Target.CONTENT);
            assertThat(item.fieldEdits())
                    .as("Turing does not own the document, so there is no field to set")
                    .isEmpty();
        });
    }

    @Test
    void aVerdictWithNoFieldEditsIsStillAValidSuggestion() {
        modelAnswers("""
                {"items":[{"target":"PERSONA","targetId":"a1",
                "rationale":"Contents average 41%.","actions":["Review the audience."]}]}""");

        Item item = onlyPersonaItem();

        assertThat(item.actions()).isNotEmpty();
        assertThat(item.fieldEdits()).isEmpty();
    }

    // ---- the deterministic path --------------------------------------------

    @Test
    void theDeterministicFallbackOffersNothingToApply() {
        when(llmSummaryService.generateStructured(anyString(), anyString(), anyString(),
                anyBoolean(), any(), any()))
                .thenReturn(new StructuredResult(
                        new SummaryResult(false, "No LLM configured.", null, false),
                        TurVerdictSource.UNAVAILABLE));

        TurPersonaMatchAdjustmentDto dto = service.suggest(PROJECT, false);

        assertThat(dto.llmUsed()).isFalse();
        assertThat(dto.items()).isNotEmpty();
        assertThat(dto.items()).allSatisfy(item -> assertThat(item.fieldEdits())
                .as("the fallback reads scores, so it can say a field looks wrong "
                        + "but never which value is right")
                .isEmpty());
    }

    // ---- the prompt --------------------------------------------------------

    @Test
    void theDigestCarriesThePersonaCurrentAudienceSoTheModelCanProposeADifferentOne() {
        modelAnswers(verdict("[]"));

        service.suggest(PROJECT, false);

        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmSummaryService).generateStructured(anyString(),
                digest.capture(), anyString(), anyBoolean(), any(), any());
        assertThat(digest.getValue())
                .contains("READING_LEVEL=GRADUATE")
                .contains("DOMAIN_EXPERTISE=EXPERT");
    }

    @Test
    void theSystemPromptNamesEveryWritableFieldAndItsAllowedValues() {
        modelAnswers(verdict("[]"));

        service.suggest(PROJECT, false);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(llmSummaryService).generateStructured(anyString(),
                anyString(), prompt.capture(), anyBoolean(), any(), any());
        assertThat(prompt.getValue())
                .contains("READING_LEVEL").contains("DOMAIN_EXPERTISE")
                .contains("VOCABULARY_CEILING").contains("COMPREHENSION_NOTES")
                .contains("UNDERGRADUATE").contains("INTERMEDIATE")
                .doesNotContain("ACCESSIBILITY_NOTES");
    }
}
