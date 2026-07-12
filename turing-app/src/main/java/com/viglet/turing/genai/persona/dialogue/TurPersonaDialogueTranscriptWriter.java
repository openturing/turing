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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.persona.dialogue.TurPersonaDialogueTurn;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueProjectRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueTurnRepository;

/**
 * Transactional writer for a Persona Dialogue project's last-run transcript
 * (Block AU / §XLIV). Lives in its own bean <em>specifically</em> so its
 * {@link Transactional} boundary is honoured: {@link TurPersonaDialogueProjectService}
 * persists from the reactive {@code doOnComplete} of a running stream, which is a
 * self-invocation on that bean — self-invoked {@code @Transactional} methods
 * bypass the Spring proxy, and the {@code @Modifying} delete then runs without a
 * transaction and throws. Calling <em>this</em> bean crosses the proxy, so the
 * delete + insert + {@code lastRunAt} stamp commit as one transaction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPersonaDialogueTranscriptWriter {

    private final TurPersonaDialogueTurnRepository turnRepository;
    private final TurPersonaDialogueProjectRepository projectRepository;

    public TurPersonaDialogueTranscriptWriter(TurPersonaDialogueTurnRepository turnRepository,
            TurPersonaDialogueProjectRepository projectRepository) {
        this.turnRepository = turnRepository;
        this.projectRepository = projectRepository;
    }

    /** Replace the project's whole transcript and stamp {@code lastRunAt}. */
    @Transactional
    public void replace(String projectId, List<TurDialogueEvent> turns) {
        turnRepository.deleteByProjectId(projectId);
        List<TurPersonaDialogueTurn> rows = new ArrayList<>(turns.size());
        for (TurDialogueEvent event : turns) {
            TurPersonaDialogueTurn row = new TurPersonaDialogueTurn();
            row.setProjectId(projectId);
            row.setTurnIndex(event.index() == null ? rows.size() : event.index());
            row.setPersonaId(event.personaId());
            row.setPersonaName(event.personaName());
            row.setContent(event.content());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            turnRepository.saveAll(rows);
        }
        projectRepository.findById(projectId).ifPresent(project -> {
            project.setLastRunAt(Instant.now());
            projectRepository.save(project);
        });
    }
}
