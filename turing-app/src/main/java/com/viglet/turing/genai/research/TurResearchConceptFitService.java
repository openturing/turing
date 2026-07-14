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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.persona.fit.TurContentFitResult;
import com.viglet.turing.genai.persona.fit.TurPersonaContentFitEvaluator;
import com.viglet.turing.genai.research.dto.TurResearchConceptFitDto;
import com.viglet.turing.genai.research.dto.TurResearchConceptFitEntryDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T727 / §XLVI.4 — the concept-test ↔ Persona Match bridge. A {@code CONCEPT_TEST}
 * study over a piece of content is content-fit's sibling: instead of forking a
 * second evaluator, this routes the study's concept text through the Block AT
 * {@link TurPersonaContentFitEvaluator}, scoring the concept against each roster
 * persona <em>as a reader</em>. Concept feedback and content-fit scoring therefore
 * share one evaluator, one cache and one verdict shape — the persona's fit %, what
 * condiz / não condiz, and grounded flagged spans.
 *
 * <p>This is a complementary lens to the T720 interview (which probes the concept
 * conversationally); here every persona reacts to the concept in one grounded pass.
 * Read-only and fail-open, mirroring {@link TurResearchInsightsService}: the report
 * is {@code available=false} (with a message) when the study is not a concept test,
 * carries no concept text, or has no roster — never a throw. It reuses the Match
 * report surface, so it has no UI of its own (the studio, T730, renders it).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchConceptFitService {

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchStudyPersonaRepository personaJoinRepository;
    private final TurPersonaRepository personaRepository;
    private final TurPersonaContentFitEvaluator contentFitEvaluator;

    public TurResearchConceptFitService(TurResearchStudyRepository studyRepository,
            TurResearchStudyPersonaRepository personaJoinRepository,
            TurPersonaRepository personaRepository,
            TurPersonaContentFitEvaluator contentFitEvaluator) {
        this.studyRepository = studyRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.personaRepository = personaRepository;
        this.contentFitEvaluator = contentFitEvaluator;
    }

    /**
     * Score the study's concept against each roster persona through the shared
     * content-fit evaluator, in roster (position) order.
     */
    @Transactional(readOnly = true)
    public TurResearchConceptFitDto conceptFit(String studyId, boolean regenerate) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        if (study == null) {
            return TurResearchConceptFitDto.unavailable("Study not found.");
        }
        if (study.getProtocol() != TurResearchProtocol.CONCEPT_TEST) {
            return TurResearchConceptFitDto.unavailable(
                    "Concept fit applies only to CONCEPT_TEST studies.");
        }
        String concept = StringUtils.trimToNull(study.getConceptText());
        if (concept == null) {
            return TurResearchConceptFitDto.unavailable(
                    "This concept-test study has no concept text yet.");
        }

        List<TurResearchConceptFitEntryDto> entries = new ArrayList<>();
        double total = 0.0;
        for (TurResearchStudyPersona join :
                personaJoinRepository.findByStudy_IdOrderByPositionAsc(studyId)) {
            TurPersona persona = personaRepository.findById(join.getPersonaId()).orElse(null);
            if (persona == null) {
                continue;
            }
            TurContentFitResult result = contentFitEvaluator.evaluateText(persona, concept,
                    "concept:" + studyId, study.getName(), regenerate);
            entries.add(new TurResearchConceptFitEntryDto(persona.getId(), persona.getName(),
                    result));
            total += result.fitScore();
        }
        if (entries.isEmpty()) {
            return TurResearchConceptFitDto.unavailable(
                    "No personas in the audience roster to score the concept against.");
        }
        double average = round(total / entries.size());
        return new TurResearchConceptFitDto(true, null, concept, average, entries);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
