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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyPersonaRefDto;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchSchedule;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * CRUD + read service for Synthetic User Research studies (Block AW / §XLVI.2,
 * T719). Owns study lifecycle and the ordered audience-roster management; the
 * cohort interview run itself lives in {@link TurResearchRunnerService}. Admin-CRUD
 * aggregate — plain JPA repositories, no domain records (Block AF stop-criteria).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchStudyService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<TurResearchTurnDto>> TURNS_TYPE =
            new TypeReference<>() {};

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchStudyPersonaRepository personaJoinRepository;
    private final TurResearchInterviewRepository interviewRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurAIAgentRepository agentRepository;

    public TurResearchStudyService(TurResearchStudyRepository studyRepository,
            TurResearchStudyPersonaRepository personaJoinRepository,
            TurResearchInterviewRepository interviewRepository,
            TurPersonaRepository personaRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurAIAgentRepository agentRepository) {
        this.studyRepository = studyRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.interviewRepository = interviewRepository;
        this.personaRepository = personaRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.agentRepository = agentRepository;
    }

    // ---- study CRUD --------------------------------------------------------

    public List<TurResearchStudyDto> list() {
        return studyRepository.findByOrderByNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<TurResearchStudyDto> get(String id) {
        return studyRepository.findById(id).map(this::toDetail);
    }

    @Transactional
    public TurResearchStudyDto create(TurResearchStudyDto dto) {
        TurResearchStudy study = new TurResearchStudy();
        apply(study, dto);
        study.setCreationDate(Instant.now());
        study.setModificationDate(Instant.now());
        return toDetail(studyRepository.save(study));
    }

    @Transactional
    public Optional<TurResearchStudyDto> update(String id, TurResearchStudyDto dto) {
        return studyRepository.findById(id).map(study -> {
            apply(study, dto);
            study.setModificationDate(Instant.now());
            return toDetail(studyRepository.save(study));
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!studyRepository.existsById(id)) {
            return false;
        }
        // Roster + interviews cascade-delete via their FKs.
        studyRepository.deleteById(id);
        return true;
    }

    private void apply(TurResearchStudy study, TurResearchStudyDto dto) {
        study.setName(StringUtils.defaultIfBlank(dto.name(), "Untitled study"));
        study.setGoal(StringUtils.trimToNull(dto.goal()));
        study.setHypothesis(StringUtils.trimToNull(dto.hypothesis()));
        study.setDescription(dto.description());
        study.setEnabled(dto.enabled());
        study.setProtocol(parseProtocol(dto.protocol()));
        study.setConceptText(StringUtils.trimToNull(dto.conceptText()));
        study.setQuestionsJson(writeQuestions(dto.questions()));
        study.setMaxQuestions(dto.maxQuestions() > 0 ? dto.maxQuestions() : 6);
        study.setLlmInstanceId(StringUtils.trimToNull(dto.llmInstanceId()));
        study.setTargetAgentId(StringUtils.trimToNull(dto.targetAgentId()));
        study.setInterviewLlmInstanceId(StringUtils.trimToNull(dto.interviewLlmInstanceId()));
        study.setSynthesisLlmInstanceId(StringUtils.trimToNull(dto.synthesisLlmInstanceId()));
        study.setSchedule(parseSchedule(dto.schedule()));
    }

    private TurResearchSchedule parseSchedule(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TurResearchSchedule.MANUAL;
        }
        try {
            return TurResearchSchedule.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurResearchSchedule.MANUAL;
        }
    }

    private TurResearchProtocol parseProtocol(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TurResearchProtocol.DYNAMIC_SCRIPT;
        }
        try {
            return TurResearchProtocol.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurResearchProtocol.DYNAMIC_SCRIPT;
        }
    }

    // ---- audience roster ---------------------------------------------------

    public List<TurResearchStudyPersonaRefDto> personas(String studyId) {
        return personaJoinRepository.findByStudy_IdOrderByPositionAsc(studyId).stream()
                .map(this::toPersonaRef)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public List<TurResearchStudyPersonaRefDto> setPersonas(String studyId,
            List<String> personaIds) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        if (study == null) {
            return List.of();
        }
        // Preserve order, drop duplicates, keep only ids that resolve to a persona.
        Set<String> ordered = new LinkedHashSet<>(personaIds == null ? List.of() : personaIds);
        List<String> valid = ordered.stream()
                .filter(StringUtils::isNotBlank)
                .filter(personaRepository::existsById)
                .toList();

        personaJoinRepository.deleteByStudyId(studyId);
        int position = 0;
        for (String personaId : valid) {
            TurResearchStudyPersona join = new TurResearchStudyPersona();
            join.setStudy(study);
            join.setPersonaId(personaId);
            join.setPosition(position++);
            personaJoinRepository.save(join);
        }
        return personas(studyId);
    }

    // ---- interviews (read) -------------------------------------------------

    /** The persisted interview transcripts (with full turns). */
    public List<TurResearchInterviewDto> interviews(String studyId) {
        return interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId).stream()
                .map(i -> toInterviewDto(i, true))
                .toList();
    }

    // ---- mapping -----------------------------------------------------------

    private TurResearchStudyDto toSummary(TurResearchStudy s) {
        return new TurResearchStudyDto(s.getId(), s.getName(), s.getGoal(), s.getHypothesis(),
                s.getDescription(), s.isEnabled(), s.getProtocol().name(), s.getConceptText(),
                parseQuestions(s.getQuestionsJson()), s.getMaxQuestions(), s.getLlmInstanceId(),
                llmName(s), s.getTargetAgentId(), targetAgentName(s),
                s.getInterviewLlmInstanceId(), s.getSynthesisLlmInstanceId(),
                s.getSchedule().name(), s.getLastRunAt(),
                personaJoinRepository.findByStudy_IdOrderByPositionAsc(s.getId()).size(),
                (int) interviewRepository.countByStudy_Id(s.getId()), null, null);
    }

    private TurResearchStudyDto toDetail(TurResearchStudy s) {
        List<TurResearchStudyPersonaRefDto> personas = personas(s.getId());
        List<TurResearchInterviewDto> interviews = interviews(s.getId());
        return new TurResearchStudyDto(s.getId(), s.getName(), s.getGoal(), s.getHypothesis(),
                s.getDescription(), s.isEnabled(), s.getProtocol().name(), s.getConceptText(),
                parseQuestions(s.getQuestionsJson()), s.getMaxQuestions(), s.getLlmInstanceId(),
                llmName(s), s.getTargetAgentId(), targetAgentName(s),
                s.getInterviewLlmInstanceId(), s.getSynthesisLlmInstanceId(),
                s.getSchedule().name(), s.getLastRunAt(),
                personas.size(), interviews.size(), personas, interviews);
    }

    private String llmName(TurResearchStudy s) {
        if (StringUtils.isBlank(s.getLlmInstanceId())) {
            return null;
        }
        return llmInstanceRepository.findById(s.getLlmInstanceId())
                .map(TurLLMInstance::getTitle)
                .orElse(null);
    }

    /** The resolved label of the T726 target agent, or null when unset/unknown. */
    private String targetAgentName(TurResearchStudy s) {
        if (StringUtils.isBlank(s.getTargetAgentId())) {
            return null;
        }
        return agentRepository.findById(s.getTargetAgentId())
                .map(TurAIAgent::getTitle)
                .orElse(null);
    }

    private TurResearchStudyPersonaRefDto toPersonaRef(TurResearchStudyPersona join) {
        TurPersona persona = personaRepository.findById(join.getPersonaId()).orElse(null);
        if (persona == null) {
            return null;
        }
        String kind = persona.getPersonaKind() == null ? null : persona.getPersonaKind().name();
        return new TurResearchStudyPersonaRefDto(persona.getId(), persona.getName(), kind,
                join.getPosition());
    }

    private TurResearchInterviewDto toInterviewDto(TurResearchInterview i, boolean withTurns) {
        String personaName = personaRepository.findById(i.getPersonaId())
                .map(TurPersona::getName).orElse(null);
        List<TurResearchTurnDto> turns = withTurns ? parseTurns(i.getTranscriptJson()) : null;
        return new TurResearchInterviewDto(i.getId(), i.getPersonaId(), personaName,
                i.getStatus() == null ? null : i.getStatus().name(), i.getTurnCount(),
                i.getError(), i.getStartedAt(), i.getCompletedAt(), turns);
    }

    // ---- json helpers ------------------------------------------------------

    private String writeQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String q : questions) {
            if (StringUtils.isNotBlank(q)) {
                cleaned.add(q.trim());
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(cleaned);
        } catch (RuntimeException e) {
            log.warn("[ResearchStudy] could not serialize questions: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseQuestions(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<TurResearchTurnDto> parseTurns(String json) {
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
}
