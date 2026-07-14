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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.eval.TurEvalDatasetImportService;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

/**
 * T725 / §XLVI.4 — promoting a completed Synthetic User Research study into a
 * reusable eval dataset (agent-QA harness). A real {@link TurEvalDatasetImportService}
 * is used so canonical-row mapping + persistence are exercised end-to-end, exactly
 * like the sibling {@code TurEvalDatasetSourceImportServiceTest}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchEvalBridgeServiceTest {

    private static final String STUDY_ID = "s1";

    @Mock
    private TurResearchStudyRepository studyRepository;
    @Mock
    private TurResearchStudyPersonaRepository personaJoinRepository;
    @Mock
    private TurResearchInterviewRepository interviewRepository;
    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurResearchInsightsService insightsService;
    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;

    private TurResearchEvalBridgeService service() {
        lenient().when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TurEvalDatasetImportService importService =
                new TurEvalDatasetImportService(datasetRepository, datasetRowRepository);
        return new TurResearchEvalBridgeService(studyRepository, personaJoinRepository,
                interviewRepository, personaRepository, insightsService, importService);
    }

    private TurResearchStudy study() {
        TurResearchStudy study = new TurResearchStudy();
        study.setId(STUDY_ID);
        study.setName("Onboarding");
        study.setGoal("Understand onboarding friction");
        study.setHypothesis("Users abandon at billing");
        study.setProtocol(TurResearchProtocol.DYNAMIC_SCRIPT);
        return study;
    }

    private void stubRoster(String... personaIds) {
        List<TurResearchStudyPersona> joins = new java.util.ArrayList<>();
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

    private TurResearchInterview interview(String personaId, TurResearchInterviewStatus status,
            String question, String answer) {
        TurResearchInterview interview = new TurResearchInterview();
        interview.setPersonaId(personaId);
        interview.setStatus(status);
        interview.setTranscriptJson(("[{\"index\":0,\"question\":\"%s\",\"answer\":\"%s\"}]")
                .formatted(question, answer));
        return interview;
    }

    private static List<TurEvalDatasetRow> rowsOf(TurEvalDataset d) {
        return List.copyOf(d.getRows());
    }

    @Test
    void completedInterviewsBecomeCandidateHarnessRows() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        stubRoster("p1", "p2");
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID)).thenReturn(List.of(
                interview("p1", TurResearchInterviewStatus.COMPLETED, "What blocks you?", "Billing is confusing"),
                interview("p2", TurResearchInterviewStatus.COMPLETED, "And you?", "Too many steps")));
        when(insightsService.report(STUDY_ID, false))
                .thenReturn(TurResearchReportDto.unavailable("no llm", false));

        TurEvalDataset dataset = service().promoteToDataset(STUDY_ID, null);

        assertThat(dataset.getName()).isEqualTo("research-Onboarding");
        List<TurEvalDatasetRow> rows = rowsOf(dataset);
        assertThat(rows).hasSize(2);
        TurEvalDatasetRow first = rows.get(0);
        assertThat(first.getName()).isEqualTo("Persona-p1");
        assertThat(first.getSeedTurnsJson()).contains("What blocks you?");
        assertThat(first.getReferenceAnswer()).contains("Billing is confusing");
        assertThat(first.getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.ANY);
        assertThat(first.getTags()).contains("research-study").contains("DYNAMIC_SCRIPT");
        assertThat(first.getMetadataJson()).contains(STUDY_ID).contains("p1");
        // Fail-open rubric degrades to the study goal/hypothesis when no report exists.
        assertThat(first.getRubric()).contains("Understand onboarding friction")
                .contains("Users abandon at billing");
    }

    @Test
    void pendingInterviewsAreSkipped() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        stubRoster("p1", "p2");
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID)).thenReturn(List.of(
                interview("p1", TurResearchInterviewStatus.COMPLETED, "q", "a"),
                interview("p2", TurResearchInterviewStatus.PENDING, "q", "a")));
        when(insightsService.report(STUDY_ID, false))
                .thenReturn(TurResearchReportDto.unavailable("no llm", false));

        List<TurEvalDatasetRow> rows = rowsOf(service().promoteToDataset(STUDY_ID, "custom-name"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("Persona-p1");
    }

    @Test
    void synthesizedRecommendationsFoldIntoRubric() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        stubRoster("p1");
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID)).thenReturn(List.of(
                interview("p1", TurResearchInterviewStatus.COMPLETED, "q", "a")));
        when(insightsService.report(STUDY_ID, false)).thenReturn(new TurResearchReportDto(
                true, null, false, "Cohort wants fewer steps.", List.of(),
                List.of("Simplify the billing screen"), List.of(), null));

        TurEvalDatasetRow row = rowsOf(service().promoteToDataset(STUDY_ID, null)).get(0);

        assertThat(row.getRubric()).contains("Cohort wants fewer steps.")
                .contains("Simplify the billing screen");
    }

    @Test
    void noCompletedInterviewsThrowsBadRequest() {
        when(studyRepository.findById(STUDY_ID)).thenReturn(Optional.of(study()));
        stubRoster("p1");
        when(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(STUDY_ID)).thenReturn(List.of(
                interview("p1", TurResearchInterviewStatus.PENDING, "q", "a")));

        TurResearchEvalBridgeService service = service();
        assertThatThrownBy(() -> service.promoteToDataset(STUDY_ID, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No completed interviews");
    }

    @Test
    void unknownStudyThrowsNotFound() {
        when(studyRepository.findById("missing")).thenReturn(Optional.empty());

        TurResearchEvalBridgeService service = service();
        assertThatThrownBy(() -> service.promoteToDataset("missing", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}
