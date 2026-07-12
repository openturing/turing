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

import com.viglet.turing.genai.persona.fit.TurContentFitMisfit;
import com.viglet.turing.genai.persona.fit.TurContentFitResult;
import com.viglet.turing.genai.persona.fit.TurPersonaContentFitEvaluator;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCell;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The N×N Persona Match analysis runner (Block AT / §XLIII, T698). Iterates the
 * Cartesian product of a project's extracted contents × referenced personas over
 * Block AA's {@link TurPersonaContentFitEvaluator} (reused unchanged), persisting
 * each result as a {@link TurPersonaMatchCell}. A content-hash guard skips
 * re-evaluating any cell whose source text is unchanged since it was last
 * computed, so scheduled re-runs are cheap. Progress streams over
 * {@link TurPersonaMatchEventBus} for a live heatmap fill.
 *
 * <p>The expensive LLM evaluations run bounded-parallel on a short-lived worker
 * pool; each worker re-establishes the caller's tenant via
 * {@link TurTenantContext#runAs} so tenant-scoped caches and instance lookups
 * resolve correctly off the request thread. All DB writes happen back on the
 * calling thread (repository {@code saveAll}) — the pool only computes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchAnalysisService {

    /** Max concurrent LLM evaluations per run. */
    private static final int PARALLELISM = 4;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> FITS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<TurContentFitMisfit>> MISFITS_TYPE =
            new TypeReference<>() {};

    private final TurPersonaMatchProjectRepository projectRepository;
    private final TurPersonaMatchSourceRepository sourceRepository;
    private final TurPersonaMatchPersonaRepository personaJoinRepository;
    private final TurPersonaMatchCellRepository cellRepository;
    private final TurPersonaRepository personaRepository;
    private final TurPersonaContentFitEvaluator evaluator;
    private final TurPersonaMatchEventBus eventBus;
    private final TurTenantContext tenantContext;

    public TurPersonaMatchAnalysisService(TurPersonaMatchProjectRepository projectRepository,
            TurPersonaMatchSourceRepository sourceRepository,
            TurPersonaMatchPersonaRepository personaJoinRepository,
            TurPersonaMatchCellRepository cellRepository,
            TurPersonaRepository personaRepository,
            TurPersonaContentFitEvaluator evaluator,
            TurPersonaMatchEventBus eventBus,
            TurTenantContext tenantContext) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.cellRepository = cellRepository;
        this.personaRepository = personaRepository;
        this.evaluator = evaluator;
        this.eventBus = eventBus;
        this.tenantContext = tenantContext;
    }

    /**
     * Run the full N×N analysis for a project, persisting every cell and stamping
     * {@code lastRunAt}. Publishes STARTED / per-cell CELL / DONE (or ERROR) over
     * the event bus. Returns the persisted matrix. Safe to call with an empty
     * project (no personas or contents) — it simply produces a zero-cell matrix.
     *
     * @param force recompute every cell and bypass the LLM cache (a full re-run)
     */
    public TurPersonaMatchMatrixDto run(String projectId, boolean force) {
        TurPersonaMatchProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            eventBus.publish(TurPersonaMatchRunEvent.error(projectId, "Project not found"));
            return new TurPersonaMatchMatrixDto(projectId, List.of());
        }
        try {
            return runInternal(project, force);
        } catch (RuntimeException e) {
            log.warn("[PersonaMatch] analysis failed for project={}: {}", projectId,
                    e.getMessage());
            eventBus.publish(TurPersonaMatchRunEvent.error(projectId, e.getMessage()));
            return getMatrix(projectId);
        }
    }

    private TurPersonaMatchMatrixDto runInternal(TurPersonaMatchProject project, boolean force) {
        String projectId = project.getId();
        List<TurPersonaMatchSource> sources = sourceRepository
                .findByProject_IdOrderBySourceNameAsc(projectId).stream()
                .filter(s -> s.getExtractionStatus() == TurPersonaSourceStatus.EXTRACTED)
                .filter(s -> StringUtils.isNotBlank(s.getCachedText()))
                .toList();
        List<TurPersona> personas = personaJoinRepository.findByProject_Id(projectId).stream()
                .map(j -> personaRepository.findById(j.getPersonaId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        int total = sources.size() * personas.size();
        eventBus.publish(TurPersonaMatchRunEvent.started(projectId, total));

        Map<String, TurPersonaMatchCell> existing = new HashMap<>();
        for (TurPersonaMatchCell cell : cellRepository.findByProjectId(projectId)) {
            existing.put(key(cell.getSourceId(), cell.getPersonaId()), cell);
        }

        List<Pair> tasks = new ArrayList<>(total);
        for (TurPersonaMatchSource source : sources) {
            for (TurPersona persona : personas) {
                tasks.add(new Pair(source, persona));
            }
        }

        List<Computed> computed = evaluateAll(project, force, tasks, existing);

        persist(project, computed, sources, personas, existing);

        List<TurPersonaMatchCellDto> cells = computed.stream().map(Computed::dto).toList();
        return new TurPersonaMatchMatrixDto(projectId, cells);
    }

    // ---- parallel evaluation ----------------------------------------------

    private List<Computed> evaluateAll(TurPersonaMatchProject project, boolean force,
            List<Pair> tasks, Map<String, TurPersonaMatchCell> existing) {
        String projectId = project.getId();
        int total = tasks.size();
        if (total == 0) {
            return List.of();
        }
        String tenant = tenantContext.getCurrentTenant();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(PARALLELISM, total));
        try {
            ExecutorCompletionService<Computed> ecs = new ExecutorCompletionService<>(pool);
            for (Pair task : tasks) {
                ecs.submit(() -> tenantContext.runAs(tenant,
                        () -> computeOrReuse(task, force, existing)));
            }
            List<Computed> results = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                Computed c = take(ecs);
                if (c != null) {
                    results.add(c);
                    eventBus.publish(TurPersonaMatchRunEvent.cell(projectId, total,
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
            log.warn("[PersonaMatch] cell evaluation error: {}",
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Computed computeOrReuse(Pair task, boolean force,
            Map<String, TurPersonaMatchCell> existing) {
        TurPersonaMatchSource source = task.source();
        TurPersona persona = task.persona();
        String hash = StringUtils.defaultIfBlank(source.getContentHash(),
                TurPersonaMatchSourceService.contentHash(source.getCachedText()));
        TurPersonaMatchCell prior = existing.get(key(source.getId(), persona.getId()));

        if (!force && prior != null && Objects.equals(prior.getContentHash(), hash)) {
            return new Computed(source.getId(), persona.getId(), toDto(prior), hash, true);
        }

        TurContentFitResult r = evaluator.evaluateText(persona, source.getCachedText(),
                source.getId(), source.getSourceName(), force);
        TurPersonaMatchCellDto dto = new TurPersonaMatchCellDto(source.getId(), persona.getId(),
                r.fitScore(), r.readabilityScore(), r.llmUsed(), r.summary(),
                nullSafe(r.fits()), r.misfits() == null ? List.of() : r.misfits());
        return new Computed(source.getId(), persona.getId(), dto, hash, false);
    }

    // ---- persistence -------------------------------------------------------

    private void persist(TurPersonaMatchProject project, List<Computed> computed,
            List<TurPersonaMatchSource> sources, List<TurPersona> personas,
            Map<String, TurPersonaMatchCell> existing) {
        Set<String> validKeys = new HashSet<>();
        for (TurPersonaMatchSource s : sources) {
            for (TurPersona p : personas) {
                validKeys.add(key(s.getId(), p.getId()));
            }
        }
        // Prune cells whose content/persona left the project.
        List<TurPersonaMatchCell> orphans = existing.entrySet().stream()
                .filter(e -> !validKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!orphans.isEmpty()) {
            cellRepository.deleteAll(orphans);
        }

        List<TurPersonaMatchCell> toSave = new ArrayList<>();
        Instant now = Instant.now();
        for (Computed c : computed) {
            if (c.reused()) {
                continue;
            }
            TurPersonaMatchCell cell = existing.getOrDefault(key(c.sourceId(), c.personaId()),
                    new TurPersonaMatchCell());
            cell.setProjectId(project.getId());
            cell.setSourceId(c.sourceId());
            cell.setPersonaId(c.personaId());
            cell.setFitScore(c.dto().fitScore());
            cell.setReadabilityScore(c.dto().readability());
            cell.setLlmUsed(c.dto().llmUsed());
            cell.setSummary(StringUtils.abbreviate(c.dto().summary(), 1000));
            cell.setFitsJson(writeJson(c.dto().fits()));
            cell.setMisfitsJson(writeJson(c.dto().misfits()));
            cell.setContentHash(c.hash());
            cell.setEvaluatedAt(now);
            toSave.add(cell);
        }
        if (!toSave.isEmpty()) {
            cellRepository.saveAll(toSave);
        }

        project.setLastRunAt(now);
        project.setModificationDate(now);
        projectRepository.save(project);
        eventBus.publish(TurPersonaMatchRunEvent.done(project.getId(),
                sources.size() * personas.size(), now));
    }

    // ---- read --------------------------------------------------------------

    /** The persisted matrix without running anything. */
    public TurPersonaMatchMatrixDto getMatrix(String projectId) {
        List<TurPersonaMatchCellDto> cells = cellRepository.findByProjectId(projectId).stream()
                .map(this::toDto)
                .toList();
        return new TurPersonaMatchMatrixDto(projectId, cells);
    }

    private TurPersonaMatchCellDto toDto(TurPersonaMatchCell cell) {
        return new TurPersonaMatchCellDto(cell.getSourceId(), cell.getPersonaId(),
                cell.getFitScore(), cell.getReadabilityScore(), cell.isLlmUsed(),
                cell.getSummary(), readFits(cell.getFitsJson()), readMisfits(cell.getMisfitsJson()));
    }

    // ---- json helpers ------------------------------------------------------

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            log.debug("[PersonaMatch] could not serialize cell payload: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readFits(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, FITS_TYPE);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<TurContentFitMisfit> readMisfits(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MISFITS_TYPE);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<String> nullSafe(List<String> in) {
        return in == null ? List.of() : in;
    }

    private static String key(String sourceId, String personaId) {
        return sourceId + "::" + personaId;
    }

    private record Pair(TurPersonaMatchSource source, TurPersona persona) {
    }

    private record Computed(String sourceId, String personaId, TurPersonaMatchCellDto dto,
            String hash, boolean reused) {
    }
}
