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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyPersonaRefDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Block AW / §XLVI.2 — end-to-end validation of the Synthetic User Research
 * backend (T719–T721) against the full Spring context: entity mapping + Liquibase
 * (the new changelog boots), study CRUD with a questions round-trip, the ordered
 * audience roster, the runner wiring (run on an empty roster is a hermetic,
 * network-free path that still stamps {@code lastRunAt}), and interview
 * persistence — a manually inserted transcript is read back with its JSON turns
 * parsed and cascade-deletes with its study.
 *
 * <p>The interview-execution path itself (interviewer sub-loop + persona answers)
 * is covered deterministically by {@link TurResearchInterviewEngineTest} with a
 * mocked chat executor, so this IT deliberately avoids driving a real LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurResearchStudyIT extends AbstractTuringSpringIT {

    @Autowired
    private TurResearchStudyService service;
    @Autowired
    private TurResearchRunnerService runnerService;
    @Autowired
    private TurPersonaRepository personaRepository;
    @Autowired
    private TurResearchStudyRepository studyRepository;
    @Autowired
    private TurResearchInterviewRepository interviewRepository;

    @Test
    void fullStudyLifecycle() {
        // --- an audience persona (a synthetic research participant) ---
        TurPersona persona = new TurPersona();
        persona.setName("Aluno iniciante (research-it)");
        persona.setDescription("Participante sintético para o estudo.");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        persona = personaRepository.save(persona);
        String personaId = persona.getId();

        // --- create a CUSTOM_SCRIPT study with a fixed question list ---
        TurResearchStudyDto created = service.create(new TurResearchStudyDto(
                null, "Research IT study", "Understand onboarding friction",
                "New users abandon at signup", "desc", true, "CUSTOM_SCRIPT", null,
                List.of("What brought you here?", "What almost stopped you?"), 6, null, null,
                null, null, null, null, null, null, 0, 0, null, null));
        String studyId = created.id();
        assertNotNull(studyId);
        assertEquals("CUSTOM_SCRIPT", created.protocol());
        assertEquals(2, created.questions().size());
        assertEquals("What brought you here?", created.questions().get(0));

        // --- ordered audience roster ---
        List<TurResearchStudyPersonaRefDto> roster =
                service.setPersonas(studyId, List.of(personaId));
        assertEquals(1, roster.size());
        assertEquals(personaId, roster.get(0).id());
        assertEquals(0, roster.get(0).position());

        // --- run the runner on an empty roster: hermetic (no LLM call) but still
        //     exercises the full run/persist path and stamps lastRunAt ---
        service.setPersonas(studyId, List.of());
        TurResearchRunResultDto result = runnerService.run(studyId, false);
        assertTrue(result.interviews().isEmpty());
        assertNotNull(studyRepository.findById(studyId).orElseThrow().getLastRunAt());

        // --- interview persistence + JSON-turn parsing (manual insert) ---
        TurResearchStudy studyRef = studyRepository.findById(studyId).orElseThrow();
        TurResearchInterview interview = new TurResearchInterview();
        interview.setStudy(studyRef);
        interview.setPersonaId(personaId);
        interview.setStatus(TurResearchInterviewStatus.COMPLETED);
        interview.setTranscriptJson(
                "[{\"index\":0,\"question\":\"Q?\",\"answer\":\"An answer.\"}]");
        interview.setTurnCount(1);
        interviewRepository.save(interview);

        List<TurResearchInterviewDto> interviews = service.interviews(studyId);
        assertEquals(1, interviews.size());
        assertEquals(TurResearchInterviewStatus.COMPLETED.name(), interviews.get(0).status());
        assertEquals(1, interviews.get(0).turns().size());
        assertEquals("Q?", interviews.get(0).turns().get(0).question());
        assertEquals("An answer.", interviews.get(0).turns().get(0).answer());

        // --- delete cascades interviews away ---
        assertTrue(service.delete(studyId));
        assertTrue(interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId).isEmpty());
        assertTrue(studyRepository.findById(studyId).isEmpty());
    }
}
