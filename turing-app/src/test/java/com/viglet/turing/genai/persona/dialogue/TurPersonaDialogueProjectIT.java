/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueProjectDto;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueSpeakerDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueProjectRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueTurnRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Block AU / §XLIV — end-to-end validation of the Persona Dialogue project
 * backend (T705) against the full Spring context: entity mapping + Liquibase
 * (new changelog boots), project CRUD, the ordered speaker roster, and the
 * transcript-persistence path (exercised directly with synthetic turn events, so
 * no LLM/network is touched — the streaming run itself is covered by the engine's
 * own tests).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPersonaDialogueProjectIT extends AbstractTuringSpringIT {

    @Autowired
    private TurPersonaDialogueProjectService service;
    @Autowired
    private TurPersonaRepository personaRepository;
    @Autowired
    private TurPersonaDialogueProjectRepository projectRepository;
    @Autowired
    private TurPersonaDialogueTurnRepository turnRepository;

    @Test
    void fullProjectLifecycle() {
        TurPersona a = speaker("Speaker A (dlg-it)");
        TurPersona b = speaker("Speaker B (dlg-it)");

        // --- create ---
        TurPersonaDialogueProjectDto created = service.create(new TurPersonaDialogueProjectDto(
                null, "Dialogue IT project", "desc", "Should we ship on Fridays?",
                null, null, 6, null, 0, null, null));
        String projectId = created.id();
        assertNotNull(projectId);
        assertEquals(6, created.turns());

        // --- ordered speakers (order preserved) ---
        List<TurPersonaDialogueSpeakerDto> speakers =
                service.setSpeakers(projectId, List.of(b.getId(), a.getId()));
        assertEquals(2, speakers.size());
        assertEquals(b.getId(), speakers.get(0).id());
        assertEquals(0, speakers.get(0).order());
        assertEquals(a.getId(), speakers.get(1).id());
        assertEquals(1, speakers.get(1).order());

        // --- persist a transcript (the completion path of a run) ---
        service.persistTranscript(projectId, List.of(
                TurDialogueEvent.turn(0, b.getId(), b.getName(), "Fridays are risky. Why not?"),
                TurDialogueEvent.turn(1, a.getId(), a.getName(), "Because rollbacks. Agreed?")));

        TurPersonaDialogueProjectDto detail = service.get(projectId).orElseThrow();
        assertEquals(2, detail.speakers().size());
        assertEquals(2, detail.transcript().size());
        assertEquals("Fridays are risky. Why not?", detail.transcript().get(0).content());
        assertNotNull(detail.lastRunAt());

        // --- re-persist replaces the transcript ---
        service.persistTranscript(projectId, List.of(
                TurDialogueEvent.turn(0, a.getId(), a.getName(), "New take. OK?")));
        assertEquals(1, turnRepository.findByProjectIdOrderByTurnIndexAsc(projectId).size());

        // --- delete cascades speakers + prunes turns ---
        assertTrue(service.delete(projectId));
        assertTrue(turnRepository.findByProjectIdOrderByTurnIndexAsc(projectId).isEmpty());
        assertFalse(projectRepository.findById(projectId).isPresent());
    }

    private TurPersona speaker(String name) {
        TurPersona persona = new TurPersona();
        persona.setName(name);
        persona.setPersonaKind(TurPersonaKind.SPEAKER);
        return personaRepository.save(persona);
    }
}
