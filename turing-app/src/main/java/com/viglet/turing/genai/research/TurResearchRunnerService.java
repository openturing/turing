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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The N-persona study runner (Block AW / §XLVI.2, T721). Interviews every
 * persona in a study's roster over the {@link TurResearchInterviewEngine} (T720),
 * persisting each transcript as a {@link TurResearchInterview}. A content-hash
 * guard skips re-interviewing any persona whose study inputs are unchanged since
 * it was last completed, so scheduled re-runs are cheap. Progress streams over
 * {@link TurResearchEventBus} for a live interview feed.
 *
 * <p>Follows the Persona Match runner pattern verbatim: the interviews run
 * bounded-parallel on a short-lived worker pool; each worker re-establishes the
 * caller's tenant via {@link TurTenantContext#runAs} so tenant-scoped instance
 * lookups resolve off the request thread. All DB writes happen back on the
 * calling thread ({@code saveAll}) — the pool only conducts interviews.
 *
 * <p>Fail-open: if no LLM instance is configured (neither the study override nor
 * a default), each interview is recorded {@code FAILED} with a clear message
 * rather than throwing — the studio surfaces the fix without losing the run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchRunnerService {

    /** Max concurrent interviews per run. */
    private static final int PARALLELISM = 4;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<TurResearchTurnDto>> TURNS_TYPE =
            new TypeReference<>() {};

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchStudyPersonaRepository personaJoinRepository;
    private final TurResearchInterviewRepository interviewRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurResearchModelLaneResolver laneResolver;
    private final TurResearchInterviewEngine engine;
    private final TurResearchDriftService driftService;
    private final TurResearchEventBus eventBus;
    private final TurTenantContext tenantContext;

    public TurResearchRunnerService(TurResearchStudyRepository studyRepository,
            TurResearchStudyPersonaRepository personaJoinRepository,
            TurResearchInterviewRepository interviewRepository,
            TurPersonaRepository personaRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurResearchModelLaneResolver laneResolver,
            TurResearchInterviewEngine engine,
            TurResearchDriftService driftService,
            TurResearchEventBus eventBus,
            TurTenantContext tenantContext) {
        this.studyRepository = studyRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.interviewRepository = interviewRepository;
        this.personaRepository = personaRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.laneResolver = laneResolver;
        this.engine = engine;
        this.driftService = driftService;
        this.eventBus = eventBus;
        this.tenantContext = tenantContext;
    }

    /**
     * Run the cohort interviews for a study, persisting every transcript and
     * stamping {@code lastRunAt}. Publishes STARTED / per-interview INTERVIEW /
     * DONE (or ERROR) over the event bus. Returns the persisted interviews. Safe
     * on an empty roster (produces a zero-interview result).
     *
     * @param force re-interview every persona, bypassing the content-hash skip
     */
    public TurResearchRunResultDto run(String studyId, boolean force) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        if (study == null) {
            eventBus.publish(TurResearchRunEvent.error(studyId, "Study not found"));
            return new TurResearchRunResultDto(studyId, List.of());
        }
        try {
            return runInternal(study, force);
        } catch (RuntimeException e) {
            log.warn("[Research] run failed for study={}: {}", studyId, e.getMessage());
            eventBus.publish(TurResearchRunEvent.error(studyId, e.getMessage()));
            return getResult(studyId);
        }
    }

    private TurResearchRunResultDto runInternal(TurResearchStudy study, boolean force) {
        String studyId = study.getId();
        List<TurPersona> personas = personaJoinRepository
                .findByStudy_IdOrderByPositionAsc(studyId).stream()
                .map(TurResearchStudyPersona::getPersonaId)
                .map(id -> personaRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        int total = personas.size();
        eventBus.publish(TurResearchRunEvent.started(studyId, total));

        TurLLMInstance llm = resolveLlm(study);
        String inputHash = inputHash(study);

        Map<String, TurResearchInterview> existing = new HashMap<>();
        for (TurResearchInterview i : interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId)) {
            existing.put(i.getPersonaId(), i);
        }

        List<Computed> computed = interviewAll(study, llm, force, personas, existing, inputHash);
        persist(study, computed, personas, existing, inputHash);

        // T729 — capture a deterministic insight-drift snapshot for this run; a
        // snapshot failure must never break the run itself.
        try {
            driftService.snapshot(studyId);
        } catch (RuntimeException e) {
            log.debug("[Research] drift snapshot failed for study={}: {}", studyId, e.getMessage());
        }

        List<TurResearchInterviewDto> interviews = computed.stream().map(Computed::dto).toList();
        return new TurResearchRunResultDto(studyId, interviews);
    }

    // ---- parallel interviews ----------------------------------------------

    private List<Computed> interviewAll(TurResearchStudy study, TurLLMInstance llm, boolean force,
            List<TurPersona> personas, Map<String, TurResearchInterview> existing,
            String inputHash) {
        String studyId = study.getId();
        int total = personas.size();
        if (total == 0) {
            return List.of();
        }
        String tenant = tenantContext.getCurrentTenant();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLELISM, total));
        try {
            ExecutorCompletionService<Computed> ecs = new ExecutorCompletionService<>(pool);
            for (TurPersona persona : personas) {
                ecs.submit(() -> tenantContext.runAs(tenant,
                        () -> interviewOrReuse(study, persona, llm, force, existing, inputHash)));
            }
            List<Computed> results = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                Computed c = take(ecs);
                if (c != null) {
                    results.add(c);
                    eventBus.publish(TurResearchRunEvent.interview(studyId, total,
                            results.size(), c.dto()));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Computed take(ExecutorCompletionService<Computed> ecs) {
        try {
            Future<Computed> f = ecs.take();
            return f.get();
        } catch (ExecutionException e) {
            log.warn("[Research] interview error: {}",
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Computed interviewOrReuse(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm, boolean force, Map<String, TurResearchInterview> existing,
            String inputHash) {
        String personaId = persona.getId();
        TurResearchInterview prior = existing.get(personaId);
        if (!force && prior != null
                && prior.getStatus() == TurResearchInterviewStatus.COMPLETED
                && Objects.equals(prior.getContentHash(), inputHash)) {
            return new Computed(personaId, toDto(prior), true);
        }

        if (llm == null) {
            return new Computed(personaId, failedDto(persona,
                    "No LLM instance configured for this study"), false,
                    TurResearchInterviewStatus.FAILED, List.of(),
                    "No LLM instance configured for this study");
        }

        try {
            List<TurResearchTurnDto> turns = engine.interview(study, persona, llm);
            return new Computed(personaId, completedDto(persona, turns), false,
                    TurResearchInterviewStatus.COMPLETED, turns, null);
        } catch (RuntimeException e) {
            log.warn("[Research] interview failed for persona={}: {}", personaId, e.getMessage());
            String reason = StringUtils.defaultIfBlank(e.getMessage(), "Interview failed");
            return new Computed(personaId, failedDto(persona, reason), false,
                    TurResearchInterviewStatus.FAILED, List.of(), reason);
        }
    }

    // ---- persistence -------------------------------------------------------

    private void persist(TurResearchStudy study, List<Computed> computed,
            List<TurPersona> personas, Map<String, TurResearchInterview> existing,
            String inputHash) {
        Set<String> validPersonaIds = new HashSet<>();
        for (TurPersona p : personas) {
            validPersonaIds.add(p.getId());
        }
        // Prune interviews for personas that left the roster.
        List<TurResearchInterview> orphans = existing.entrySet().stream()
                .filter(e -> !validPersonaIds.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!orphans.isEmpty()) {
            interviewRepository.deleteAll(orphans);
        }

        List<TurResearchInterview> toSave = new ArrayList<>();
        Instant now = Instant.now();
        for (Computed c : computed) {
            if (c.reused()) {
                continue;
            }
            TurResearchInterview interview = existing.getOrDefault(c.personaId(),
                    newInterview(study, c.personaId()));
            interview.setStudy(study);
            interview.setPersonaId(c.personaId());
            interview.setStatus(c.status());
            interview.setTranscriptJson(writeJson(c.turns()));
            interview.setTurnCount(c.turns() == null ? 0 : c.turns().size());
            interview.setContentHash(inputHash);
            interview.setError(StringUtils.abbreviate(c.error(), 500));
            if (interview.getStartedAt() == null) {
                interview.setStartedAt(now);
            }
            interview.setCompletedAt(now);
            toSave.add(interview);
        }
        if (!toSave.isEmpty()) {
            interviewRepository.saveAll(toSave);
        }

        study.setLastRunAt(now);
        study.setModificationDate(now);
        studyRepository.save(study);
        eventBus.publish(TurResearchRunEvent.done(study.getId(), personas.size(), now));
    }

    private TurResearchInterview newInterview(TurResearchStudy study, String personaId) {
        TurResearchInterview interview = new TurResearchInterview();
        interview.setStudy(study);
        interview.setPersonaId(personaId);
        return interview;
    }

    // ---- read --------------------------------------------------------------

    /** The persisted interviews without running anything. */
    public TurResearchRunResultDto getResult(String studyId) {
        List<TurResearchInterviewDto> interviews = interviewRepository
                .findByStudy_IdOrderByPersonaIdAsc(studyId).stream()
                .map(this::toDto)
                .toList();
        return new TurResearchRunResultDto(studyId, interviews);
    }

    // ---- llm resolution ----------------------------------------------------

    private TurLLMInstance resolveLlm(TurResearchStudy study) {
        // T728 — the interview stage rides its own lane: interview instance →
        // study-wide instance → default LLM (all fail-open in the resolver).
        String id = laneResolver.resolveInstanceId(study, TurResearchStage.INTERVIEW);
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return llmInstanceRepository.findById(id).orElse(null);
    }

    // ---- input hash --------------------------------------------------------

    /**
     * Stable hash of the study inputs that determine an interview's content
     * (protocol + goal/hypothesis + concept + questions + question cap + T726
     * target agent). Shared by every persona in a run, so an unchanged re-run
     * reuses each completed transcript; changing the target forces a re-run.
     */
    private String inputHash(TurResearchStudy study) {
        String material = study.getProtocol() + "|" + StringUtils.defaultString(study.getGoal())
                + "|" + StringUtils.defaultString(study.getHypothesis())
                + "|" + StringUtils.defaultString(study.getConceptText())
                + "|" + StringUtils.defaultString(study.getQuestionsJson())
                + "|" + study.getMaxQuestions()
                + "|" + StringUtils.defaultString(study.getTargetAgentId())
                + "|" + StringUtils.defaultString(study.getInterviewLlmInstanceId());
        return Integer.toHexString(material.length()) + "-"
                + Integer.toHexString(material.hashCode());
    }

    // ---- dto helpers -------------------------------------------------------

    private TurResearchInterviewDto completedDto(TurPersona persona,
            List<TurResearchTurnDto> turns) {
        return new TurResearchInterviewDto(null, persona.getId(), persona.getName(),
                TurResearchInterviewStatus.COMPLETED.name(), turns == null ? 0 : turns.size(), null,
                null, null, turns);
    }

    private TurResearchInterviewDto failedDto(TurPersona persona, String error) {
        return new TurResearchInterviewDto(null, persona.getId(), persona.getName(),
                TurResearchInterviewStatus.FAILED.name(), 0, error, null, null, List.of());
    }

    private TurResearchInterviewDto toDto(TurResearchInterview i) {
        String personaName = personaRepository.findById(i.getPersonaId())
                .map(TurPersona::getName).orElse(null);
        return new TurResearchInterviewDto(i.getId(), i.getPersonaId(), personaName,
                i.getStatus() == null ? null : i.getStatus().name(), i.getTurnCount(),
                i.getError(), i.getStartedAt(), i.getCompletedAt(), readTurns(i.getTranscriptJson()));
    }

    private String writeJson(List<TurResearchTurnDto> turns) {
        if (turns == null || turns.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(turns);
        } catch (RuntimeException e) {
            log.debug("[Research] could not serialize transcript: {}", e.getMessage());
            return null;
        }
    }

    private List<TurResearchTurnDto> readTurns(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<TurResearchTurnDto> parsed = MAPPER.readValue(json, TURNS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private record Computed(String personaId, TurResearchInterviewDto dto, boolean reused,
            TurResearchInterviewStatus status, List<TurResearchTurnDto> turns, String error) {

        /** Reused (skipped) computed — carries only the prior DTO. */
        Computed(String personaId, TurResearchInterviewDto dto, boolean reused) {
            this(personaId, dto, reused, null, null, null);
        }
    }
}
