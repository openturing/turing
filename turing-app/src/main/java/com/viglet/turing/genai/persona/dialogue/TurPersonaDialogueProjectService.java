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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.persona.dialogue.TurDialogueEvent.TurDialogueEventType;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueProjectDto;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueSpeakerDto;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueTurnDto;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.dialogue.TurPersonaDialogueProject;
import com.viglet.turing.persistence.model.persona.dialogue.TurPersonaDialogueSpeaker;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueProjectRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueSpeakerRepository;
import com.viglet.turing.persistence.repository.persona.dialogue.TurPersonaDialogueTurnRepository;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * CRUD + run service for Persona Dialogue projects (Block AU / §XLIV, T705). Owns
 * project/speaker management, maps to DTOs, and runs a project by delegating to
 * the unchanged round-robin streaming engine ({@link TurPersonaDialogueService})
 * — additionally persisting the streamed transcript so re-opening a project shows
 * its last conversation. Admin-CRUD aggregate — plain JPA repositories.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaDialogueProjectService {

    private final TurPersonaDialogueProjectRepository projectRepository;
    private final TurPersonaDialogueSpeakerRepository speakerRepository;
    private final TurPersonaDialogueTurnRepository turnRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurPersonaDialogueService dialogueService;
    private final TurPersonaDialogueTranscriptWriter transcriptWriter;
    private final TurTenantContext tenantContext;

    public TurPersonaDialogueProjectService(
            TurPersonaDialogueProjectRepository projectRepository,
            TurPersonaDialogueSpeakerRepository speakerRepository,
            TurPersonaDialogueTurnRepository turnRepository,
            TurPersonaRepository personaRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurPersonaDialogueService dialogueService,
            TurPersonaDialogueTranscriptWriter transcriptWriter,
            TurTenantContext tenantContext) {
        this.projectRepository = projectRepository;
        this.speakerRepository = speakerRepository;
        this.turnRepository = turnRepository;
        this.personaRepository = personaRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.dialogueService = dialogueService;
        this.transcriptWriter = transcriptWriter;
        this.tenantContext = tenantContext;
    }

    // ---- project CRUD ------------------------------------------------------

    public List<TurPersonaDialogueProjectDto> list() {
        return projectRepository.findByOrderByNameAsc().stream().map(this::toSummary).toList();
    }

    public Optional<TurPersonaDialogueProjectDto> get(String id) {
        return projectRepository.findById(id).map(this::toDetail);
    }

    @Transactional
    public TurPersonaDialogueProjectDto create(TurPersonaDialogueProjectDto dto) {
        TurPersonaDialogueProject project = new TurPersonaDialogueProject();
        apply(project, dto);
        project.setCreationDate(Instant.now());
        project.setModificationDate(Instant.now());
        return toDetail(projectRepository.save(project));
    }

    @Transactional
    public Optional<TurPersonaDialogueProjectDto> update(String id,
            TurPersonaDialogueProjectDto dto) {
        return projectRepository.findById(id).map(project -> {
            apply(project, dto);
            project.setModificationDate(Instant.now());
            return toDetail(projectRepository.save(project));
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!projectRepository.existsById(id)) {
            return false;
        }
        turnRepository.deleteByProjectId(id); // turns have no FK cascade
        projectRepository.deleteById(id);     // speakers cascade via FK
        return true;
    }

    private void apply(TurPersonaDialogueProject project, TurPersonaDialogueProjectDto dto) {
        project.setName(StringUtils.defaultIfBlank(dto.name(), "Untitled dialogue"));
        project.setDescription(dto.description());
        project.setTopic(dto.topic());
        project.setLlmInstanceId(StringUtils.trimToNull(dto.llmInstanceId()));
        project.setTurns(clampRounds(dto.turns()));
    }

    /**
     * The project's {@code turns} field is the number of <b>rounds</b> (Block AU):
     * one round = every persona speaks once, in order, so nobody is left out on an
     * incomplete final round. Default 5, clamped to [1, 20].
     */
    private static int clampRounds(int rounds) {
        if (rounds <= 0) {
            return 5;
        }
        return Math.min(20, rounds);
    }

    // ---- speakers ----------------------------------------------------------

    public List<TurPersonaDialogueSpeakerDto> speakers(String projectId) {
        List<TurPersonaDialogueSpeaker> rows =
                speakerRepository.findByProject_IdOrderBySpeakerOrderAsc(projectId);
        List<TurPersonaDialogueSpeakerDto> out = new ArrayList<>(rows.size());
        for (TurPersonaDialogueSpeaker row : rows) {
            TurPersona persona = personaRepository.findById(row.getPersonaId()).orElse(null);
            if (persona != null) {
                out.add(new TurPersonaDialogueSpeakerDto(persona.getId(), persona.getName(),
                        persona.getPersonaKind() == null ? null : persona.getPersonaKind().name(),
                        row.getSpeakerOrder()));
            }
        }
        return out;
    }

    /** Replace the ordered roster; the array order is the speaking order. */
    @Transactional
    public List<TurPersonaDialogueSpeakerDto> setSpeakers(String projectId,
            List<String> personaIds) {
        TurPersonaDialogueProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return List.of();
        }
        // De-dupe preserving order; keep only ids that resolve to a real persona.
        LinkedHashSet<String> ordered = new LinkedHashSet<>(
                personaIds == null ? List.of() : personaIds);
        speakerRepository.deleteByProjectId(projectId);
        int order = 0;
        for (String personaId : ordered) {
            if (!personaRepository.existsById(personaId)) {
                continue;
            }
            TurPersonaDialogueSpeaker speaker = new TurPersonaDialogueSpeaker();
            speaker.setProject(project);
            speaker.setPersonaId(personaId);
            speaker.setSpeakerOrder(order++);
            speakerRepository.save(speaker);
        }
        return speakers(projectId);
    }

    // ---- transcript --------------------------------------------------------

    public List<TurPersonaDialogueTurnDto> transcript(String projectId) {
        return turnRepository.findByProjectIdOrderByTurnIndexAsc(projectId).stream()
                .map(t -> new TurPersonaDialogueTurnDto(t.getTurnIndex(), t.getPersonaId(),
                        t.getPersonaName(), t.getContent()))
                .toList();
    }

    // ---- run ---------------------------------------------------------------

    /**
     * Run a project's dialogue: load its saved topic / ordered speakers / model /
     * turn budget, stream via the engine, and persist the resulting transcript on
     * completion (replacing the previous one). Returns the same live SSE stream
     * the ephemeral endpoint does.
     */
    public Flux<TurDialogueEvent> run(String projectId) {
        TurPersonaDialogueProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Dialogue project not found: "
                        + projectId));
        List<String> speakerIds = speakerRepository
                .findByProject_IdOrderBySpeakerOrderAsc(projectId).stream()
                .map(TurPersonaDialogueSpeaker::getPersonaId)
                .toList();

        String tenant = tenantContext.getCurrentTenant();
        List<TurDialogueEvent> collected = Collections.synchronizedList(new ArrayList<>());

        // `turns` is the number of rounds; one round = every persona speaks once,
        // so the engine's total utterance budget is rounds × speakers (complete
        // rounds — no persona is dropped on an incomplete final round).
        int totalUtterances = clampRounds(project.getTurns()) * Math.max(1, speakerIds.size());

        return dialogueService.stream(project.getTopic(), speakerIds,
                        project.getLlmInstanceId(), totalUtterances)
                .doOnNext(event -> {
                    if (event.type() == TurDialogueEventType.TURN) {
                        collected.add(event);
                    }
                })
                // doFinally (not doOnComplete): the SSE endpoint wraps this with a
                // takeUntil(terminal) that CANCELS upstream the instant the DONE
                // event passes, which would skip doOnComplete. doFinally still
                // fires on that cancel, so the transcript is persisted either way.
                .doFinally(signal -> persistTranscript(projectId, collected, tenant));
    }

    /**
     * Persist the streamed transcript on stream completion. Runs on the reactive
     * worker thread, so it re-establishes the caller's tenant and delegates to the
     * {@link TurPersonaDialogueTranscriptWriter proxied writer} (a self-invoked
     * {@code @Transactional} method here would bypass the proxy and run the delete
     * without a transaction). A failure is logged and swallowed — the client has
     * already received every turn, so it must never corrupt the SSE response.
     */
    private void persistTranscript(String projectId, List<TurDialogueEvent> turns, String tenant) {
        try {
            tenantContext.runAs(tenant, () -> {
                transcriptWriter.replace(projectId, turns);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("[PersonaDialogue] could not persist transcript for project={}: {}",
                    projectId, e.getMessage());
        }
    }

    /** Test/seam entry point — delegates to the transactional writer. */
    public void persistTranscript(String projectId, List<TurDialogueEvent> turns) {
        transcriptWriter.replace(projectId, turns);
    }

    // ---- mapping -----------------------------------------------------------

    private TurPersonaDialogueProjectDto toSummary(TurPersonaDialogueProject p) {
        return new TurPersonaDialogueProjectDto(p.getId(), p.getName(), p.getDescription(),
                p.getTopic(), p.getLlmInstanceId(), llmName(p), p.getTurns(), p.getLastRunAt(),
                speakerRepository.findByProject_IdOrderBySpeakerOrderAsc(p.getId()).size(),
                null, null);
    }

    private TurPersonaDialogueProjectDto toDetail(TurPersonaDialogueProject p) {
        List<TurPersonaDialogueSpeakerDto> speakers = speakers(p.getId());
        return new TurPersonaDialogueProjectDto(p.getId(), p.getName(), p.getDescription(),
                p.getTopic(), p.getLlmInstanceId(), llmName(p), p.getTurns(), p.getLastRunAt(),
                speakers.size(), speakers, transcript(p.getId()));
    }

    private String llmName(TurPersonaDialogueProject p) {
        if (StringUtils.isBlank(p.getLlmInstanceId())) {
            return null;
        }
        return llmInstanceRepository.findById(p.getLlmInstanceId())
                .map(TurLLMInstance::getTitle)
                .orElse(null);
    }
}
