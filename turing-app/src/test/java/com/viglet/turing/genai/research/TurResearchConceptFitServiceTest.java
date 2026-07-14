/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.fit.TurContentFitResult;
import com.viglet.turing.genai.persona.fit.TurPersonaContentFitEvaluator;
import com.viglet.turing.genai.research.dto.TurResearchConceptFitDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

/**
 * T727 / §XLVI.4 — routing a CONCEPT_TEST study's concept through the shared Block
 * AT content-fit evaluator, once per roster persona. The evaluator is mocked (its
 * own behaviour is covered by its own tests); these verify the bridge wiring,
 * roster iteration, mean, and the fail-open guards.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchConceptFitServiceTest {

    private static final String STUDY_ID = "s1";

    @Mock
    private TurResearchStudyRepository studyRepository;
    @Mock
    private TurResearchStudyPersonaRepository personaJoinRepository;
    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurPersonaContentFitEvaluator contentFitEvaluator;

    private TurResearchConceptFitService service() {
        return new TurResearchConceptFitService(studyRepository, personaJoinRepository,
                personaRepository, contentFitEvaluator);
    }

    private TurResearchStudy conceptStudy() {
        TurResearchStudy study = new TurResearchStudy();
        study.setId(STUDY_ID);
        study.setName("Pricing concept");
        study.setProtocol(TurResearchProtocol.CONCEPT_TEST);
        study.setConceptText("A flat $9/month plan with unlimited searches");
        return study;
    }

    private void stubRoster(String... personaIds) {
        java.util.List<TurResearchStudyPersona> joins = new java.util.ArrayList<>();
        for (int i = 0; i < personaIds.length; i++) {
            TurResearchStudyPersona join = new TurResearchStudyPersona();
            join.setPersonaId(personaIds[i]);
            join.setPosition(i);
            joins.add(join);
            TurPersona persona = new TurPersona();
            persona.setId(personaIds[i]);
            persona.setName("Persona-" + personaIds[i]);
            lenient().when(personaRepository.findById(personaIds[i]))
                    .thenReturn(Optional.of(persona));
        }
        when(personaJoinRepository.findByStudy_IdOrderByPositionAsc(STUDY_ID)).thenReturn(joins);
    }

    private static TurContentFitResult fit(double score) {
        return new TurContentFitResult(score, "ok", List.of(), List.of(), score, null,
                true, null, false, "concept:s1", "Pricing concept");
    }

    @Test
    void scoresConceptAgainstEachRosterPersona() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(conceptStudy()));
        stubRoster("p1", "p2");
        when(contentFitEvaluator.evaluateText(any(), eq("A flat $9/month plan with unlimited searches"),
                eq("concept:" + STUDY_ID), eq("Pricing concept"), anyBoolean()))
                .thenReturn(fit(80.0), fit(60.0));

        TurResearchConceptFitDto dto = service().conceptFit(STUDY_ID, false);

        assertThat(dto.available()).isTrue();
        assertThat(dto.conceptText()).isEqualTo("A flat $9/month plan with unlimited searches");
        assertThat(dto.personas()).hasSize(2);
        assertThat(dto.personas().get(0).personaName()).isEqualTo("Persona-p1");
        assertThat(dto.averageFitScore()).isEqualTo(70.0);
    }

    @Test
    void nonConceptTestStudyIsUnavailable() {
        TurResearchStudy study = conceptStudy();
        study.setProtocol(TurResearchProtocol.DYNAMIC_SCRIPT);
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study));

        TurResearchConceptFitDto dto = service().conceptFit(STUDY_ID, false);

        assertThat(dto.available()).isFalse();
        assertThat(dto.error()).contains("CONCEPT_TEST");
    }

    @Test
    void blankConceptIsUnavailable() {
        TurResearchStudy study = conceptStudy();
        study.setConceptText("   ");
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study));

        TurResearchConceptFitDto dto = service().conceptFit(STUDY_ID, false);

        assertThat(dto.available()).isFalse();
        assertThat(dto.error()).contains("no concept text");
    }

    @Test
    void emptyRosterIsUnavailable() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(conceptStudy()));
        when(personaJoinRepository.findByStudy_IdOrderByPositionAsc(STUDY_ID)).thenReturn(List.of());

        TurResearchConceptFitDto dto = service().conceptFit(STUDY_ID, false);

        assertThat(dto.available()).isFalse();
        assertThat(dto.error()).contains("roster");
    }

    @Test
    void unknownStudyIsUnavailable() {
        when(studyRepository.findById("missing")).thenReturn(Optional.empty());

        TurResearchConceptFitDto dto = service().conceptFit("missing", false);

        assertThat(dto.available()).isFalse();
        assertThat(dto.error()).contains("not found");
    }
}
