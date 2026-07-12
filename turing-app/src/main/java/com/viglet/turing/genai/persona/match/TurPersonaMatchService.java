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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchPersonaRefDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchProjectDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto.ByContent;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto.ByPersona;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto.Ranked;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchSourceDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchSourceRequest;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceType;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchPersona;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSchedule;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchCellRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * CRUD + report service for Persona Match projects (Block AT / §XLIII, T699).
 * Owns project/source/persona-set management and the two bidirectional report
 * aggregations over persisted cells; the N×N run itself lives in
 * {@link TurPersonaMatchAnalysisService}. Admin-CRUD aggregate — plain JPA
 * repositories, no domain records.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchService {

    private final TurPersonaMatchProjectRepository projectRepository;
    private final TurPersonaMatchSourceRepository sourceRepository;
    private final TurPersonaMatchPersonaRepository personaJoinRepository;
    private final TurPersonaMatchCellRepository cellRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurPersonaMatchSourceService sourceService;
    private final TurPersonaMatchAnalysisService analysisService;

    public TurPersonaMatchService(TurPersonaMatchProjectRepository projectRepository,
            TurPersonaMatchSourceRepository sourceRepository,
            TurPersonaMatchPersonaRepository personaJoinRepository,
            TurPersonaMatchCellRepository cellRepository,
            TurPersonaRepository personaRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurPersonaMatchSourceService sourceService,
            TurPersonaMatchAnalysisService analysisService) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.cellRepository = cellRepository;
        this.personaRepository = personaRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.sourceService = sourceService;
        this.analysisService = analysisService;
    }

    // ---- project CRUD ------------------------------------------------------

    public List<TurPersonaMatchProjectDto> list() {
        return projectRepository.findByOrderByNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<TurPersonaMatchProjectDto> get(String id) {
        return projectRepository.findById(id).map(this::toDetail);
    }

    @Transactional
    public TurPersonaMatchProjectDto create(TurPersonaMatchProjectDto dto) {
        TurPersonaMatchProject project = new TurPersonaMatchProject();
        apply(project, dto);
        project.setCreationDate(Instant.now());
        project.setModificationDate(Instant.now());
        return toDetail(projectRepository.save(project));
    }

    @Transactional
    public Optional<TurPersonaMatchProjectDto> update(String id, TurPersonaMatchProjectDto dto) {
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
        cellRepository.deleteByProjectId(id); // cells have no FK cascade
        projectRepository.deleteById(id);     // sources + joins cascade via FK
        return true;
    }

    private void apply(TurPersonaMatchProject project, TurPersonaMatchProjectDto dto) {
        project.setName(StringUtils.defaultIfBlank(dto.name(), "Untitled project"));
        project.setDescription(dto.description());
        project.setEnabled(dto.enabled());
        project.setSchedule(parseSchedule(dto.schedule()));
        project.setLlmInstanceId(StringUtils.trimToNull(dto.llmInstanceId()));
    }

    private TurPersonaMatchSchedule parseSchedule(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TurPersonaMatchSchedule.MANUAL;
        }
        try {
            return TurPersonaMatchSchedule.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurPersonaMatchSchedule.MANUAL;
        }
    }

    // ---- persona set -------------------------------------------------------

    public List<TurPersonaMatchPersonaRefDto> personas(String projectId) {
        return personaJoinRepository.findByProject_Id(projectId).stream()
                .map(j -> personaRepository.findById(j.getPersonaId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toPersonaRef)
                .sorted(Comparator.comparing(TurPersonaMatchPersonaRefDto::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public List<TurPersonaMatchPersonaRefDto> setPersonas(String projectId,
            List<String> personaIds) {
        TurPersonaMatchProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return List.of();
        }
        Set<String> requested = new LinkedHashSet<>(personaIds == null ? List.of() : personaIds);
        // Keep only ids that resolve to a real persona.
        Set<String> valid = requested.stream()
                .filter(personaRepository::existsById)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> current = personaJoinRepository.findByProject_Id(projectId).stream()
                .map(TurPersonaMatchPersona::getPersonaId)
                .collect(Collectors.toSet());

        // Prune cells for personas being removed.
        current.stream().filter(id -> !valid.contains(id))
                .forEach(id -> cellRepository.deleteByProjectIdAndPersonaId(projectId, id));

        personaJoinRepository.deleteByProjectId(projectId);
        for (String personaId : valid) {
            TurPersonaMatchPersona join = new TurPersonaMatchPersona();
            join.setProject(project);
            join.setPersonaId(personaId);
            personaJoinRepository.save(join);
        }
        return personas(projectId);
    }

    // ---- sources -----------------------------------------------------------

    public List<TurPersonaMatchSourceDto> sources(String projectId) {
        return sourceRepository.findByProject_IdOrderBySourceNameAsc(projectId).stream()
                .map(this::toSourceDto)
                .toList();
    }

    @Transactional
    public Optional<TurPersonaMatchSourceDto> addSource(String projectId,
            TurPersonaMatchSourceRequest req) {
        return projectRepository.findById(projectId).map(project -> {
            TurPersonaMatchSource source = new TurPersonaMatchSource();
            source.setProject(project);
            source.setType(parseType(req.type()));
            source.setSourceName(StringUtils.defaultIfBlank(req.sourceName(), req.ref()));
            source.setRef(req.ref());
            source.setSiteName(req.siteName());
            sourceService.extract(source);
            return toSourceDto(sourceRepository.save(source));
        });
    }

    @Transactional
    public Optional<TurPersonaMatchSourceDto> uploadSource(String projectId, MultipartFile file) {
        return projectRepository.findById(projectId).map(project -> {
            TurPersonaMatchSource source = new TurPersonaMatchSource();
            source.setProject(project);
            source.setType(TurPersonaSourceType.ASSET);
            source.setSourceName(file.getOriginalFilename());
            source.setRef(file.getOriginalFilename());
            sourceService.extractFromUpload(source, file);
            return toSourceDto(sourceRepository.save(source));
        });
    }

    @Transactional
    public Optional<TurPersonaMatchSourceDto> reExtract(String projectId, String sourceId) {
        return sourceRepository.findById(sourceId)
                .filter(s -> s.getProject() != null && projectId.equals(s.getProject().getId()))
                .map(source -> {
                    sourceService.extract(source);
                    return toSourceDto(sourceRepository.save(source));
                });
    }

    @Transactional
    public boolean deleteSource(String projectId, String sourceId) {
        return sourceRepository.findById(sourceId)
                .filter(s -> s.getProject() != null && projectId.equals(s.getProject().getId()))
                .map(source -> {
                    cellRepository.deleteByProjectIdAndSourceId(projectId, sourceId);
                    sourceRepository.delete(source);
                    return true;
                })
                .orElse(false);
    }

    // ---- report ------------------------------------------------------------

    /** Both bidirectional lenses over the persisted matrix cells. */
    public TurPersonaMatchReportDto report(String projectId) {
        List<TurPersonaMatchCellDto> cells = analysisService.getMatrix(projectId).cells();

        Map<String, List<TurPersonaMatchCellDto>> bySource = cells.stream()
                .collect(Collectors.groupingBy(TurPersonaMatchCellDto::sourceId));
        List<ByContent> byContent = bySource.entrySet().stream()
                .map(e -> new ByContent(e.getKey(), avg(e.getValue()),
                        e.getValue().stream()
                                .sorted(Comparator.comparingDouble(
                                        TurPersonaMatchCellDto::fitScore).reversed())
                                .map(c -> new Ranked(c.personaId(), c.fitScore()))
                                .toList()))
                .sorted(Comparator.comparing(ByContent::sourceId))
                .toList();

        Map<String, List<TurPersonaMatchCellDto>> byPersonaId = cells.stream()
                .collect(Collectors.groupingBy(TurPersonaMatchCellDto::personaId));
        List<ByPersona> byPersona = byPersonaId.entrySet().stream()
                .map(e -> new ByPersona(e.getKey(), avg(e.getValue()),
                        e.getValue().stream()
                                .sorted(Comparator.comparingDouble(
                                        TurPersonaMatchCellDto::fitScore).reversed())
                                .map(c -> new Ranked(c.sourceId(), c.fitScore()))
                                .toList()))
                .sorted(Comparator.comparing(ByPersona::personaId))
                .toList();

        return new TurPersonaMatchReportDto(byContent, byPersona);
    }

    private double avg(List<TurPersonaMatchCellDto> cells) {
        if (cells.isEmpty()) {
            return 0;
        }
        double sum = cells.stream().mapToDouble(TurPersonaMatchCellDto::fitScore).sum();
        return Math.round((sum / cells.size()) * 100.0) / 100.0;
    }

    // ---- mapping -----------------------------------------------------------

    private TurPersonaMatchProjectDto toSummary(TurPersonaMatchProject p) {
        return new TurPersonaMatchProjectDto(p.getId(), p.getName(), p.getDescription(),
                p.isEnabled(), p.getSchedule().name(), p.getLlmInstanceId(), llmName(p),
                p.getLastRunAt(), (int) sourceRepository.countByProject_Id(p.getId()),
                personaJoinRepository.findByProject_Id(p.getId()).size(), null, null);
    }

    private TurPersonaMatchProjectDto toDetail(TurPersonaMatchProject p) {
        List<TurPersonaMatchSourceDto> sources = sources(p.getId());
        List<TurPersonaMatchPersonaRefDto> personas = personas(p.getId());
        return new TurPersonaMatchProjectDto(p.getId(), p.getName(), p.getDescription(),
                p.isEnabled(), p.getSchedule().name(), p.getLlmInstanceId(), llmName(p),
                p.getLastRunAt(), sources.size(), personas.size(), sources, personas);
    }

    private String llmName(TurPersonaMatchProject p) {
        if (StringUtils.isBlank(p.getLlmInstanceId())) {
            return null;
        }
        return llmInstanceRepository.findById(p.getLlmInstanceId())
                .map(TurLLMInstance::getTitle)
                .orElse(null);
    }

    private TurPersonaMatchSourceDto toSourceDto(TurPersonaMatchSource s) {
        return new TurPersonaMatchSourceDto(s.getId(), s.getType() == null ? null : s.getType().name(),
                s.getSourceName(), s.getRef(), s.getSiteName(), s.getCachedTextLength(),
                s.getExtractionStatus() == null ? null : s.getExtractionStatus().name(),
                s.getExtractionError());
    }

    private TurPersonaMatchPersonaRefDto toPersonaRef(TurPersona persona) {
        String kind = persona.getPersonaKind() == null ? null : persona.getPersonaKind().name();
        return new TurPersonaMatchPersonaRefDto(persona.getId(), persona.getName(), kind);
    }

    private TurPersonaSourceType parseType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TurPersonaSourceType.URL;
        }
        try {
            return TurPersonaSourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurPersonaSourceType.URL;
        }
    }
}
