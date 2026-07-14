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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.eval.TurEvalDatasetImportService;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T725 / §XLVI.4 — the studies → evaluation bridge, the first of Block AW's two
 * interconnection tasks (the part a standalone research SaaS cannot do, because it
 * owns neither an agent runtime nor an eval platform).
 *
 * <p>Promotes a completed Synthetic User Research study into a reusable
 * {@link TurEvalDataset} (Block AJ / T595–T596): the persona cohort stops being a
 * one-shot report and becomes an <em>agent-QA harness</em> — a repeatable
 * regression test of an agent against a synthetic audience. One completed
 * interview becomes one candidate eval row:
 *
 * <ul>
 *   <li>the interviewer's questions become the row's replay {@code turns} (the
 *       prompts an agent-under-test would be driven with);</li>
 *   <li>the persona's answers are preserved verbatim as the {@code referenceAnswer}
 *       — reference material a reference-answer / embedding grader can use once
 *       curated, not an assertion;</li>
 *   <li>a study-level {@code rubric} (goal + hypothesis + the T722 synthesized
 *       recommendations, when a report is available) grounds a model-judge grader
 *       in the research findings;</li>
 *   <li>{@code expectedOutcome} stays {@code ANY} so, exactly like a mined-failure
 *       row ({@link com.viglet.turing.genai.eval.TurEvalDatasetSourceImportService}),
 *       the row asserts nothing until a human curates it in the Eval Studio.</li>
 * </ul>
 *
 * <p>Persistence + canonical-row mapping are delegated to
 * {@link TurEvalDatasetImportService#importCanonicalRows}, so a promoted dataset is
 * byte-identical in shape to an uploaded or mined one and round-trips through the
 * same export. Fail-open on the findings side: when no default LLM is configured
 * (or synthesis fails) the rubric degrades to the study goal/hypothesis rather than
 * fabricating criteria. <b>UI:</b> a "promote to dataset / run as eval" action in
 * the studio (T730) that deep-links into the existing Eval Studio
 * ({@code /bento/eval}) — no new eval UI here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchEvalBridgeService {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    private static final TypeReference<List<TurResearchTurnDto>> TURNS_TYPE =
            new TypeReference<>() {};

    /** Cap on preserved reference-answer material per row (chars). */
    private static final int MAX_REFERENCE_CHARS = 4_000;

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchStudyPersonaRepository personaJoinRepository;
    private final TurResearchInterviewRepository interviewRepository;
    private final TurPersonaRepository personaRepository;
    private final TurResearchInsightsService insightsService;
    private final TurEvalDatasetImportService importService;

    public TurResearchEvalBridgeService(TurResearchStudyRepository studyRepository,
            TurResearchStudyPersonaRepository personaJoinRepository,
            TurResearchInterviewRepository interviewRepository,
            TurPersonaRepository personaRepository,
            TurResearchInsightsService insightsService,
            TurEvalDatasetImportService importService) {
        this.studyRepository = studyRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.interviewRepository = interviewRepository;
        this.personaRepository = personaRepository;
        this.insightsService = insightsService;
        this.importService = importService;
    }

    /**
     * Promotes the study's completed interviews into a new eval dataset. Throws
     * {@code 404} when the study is unknown and {@code 400} when there is nothing to
     * promote (no completed interview carries any captured turn).
     *
     * @param studyId the study to promote
     * @param name    an explicit dataset name, or {@code null} to derive one
     * @return the persisted dataset (rows attached)
     */
    @Transactional
    public TurEvalDataset promoteToDataset(String studyId, String name) {
        TurResearchStudy study = studyRepository.findById(studyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Research study not found: " + studyId));

        List<Interview> completed = orderedCompletedInterviews(studyId);
        String rubric = buildRubric(study);

        List<ObjectNode> rows = new ArrayList<>();
        for (Interview interview : completed) {
            ObjectNode row = buildRow(study, interview, rubric);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No completed interviews with captured turns to promote — run the study first");
        }
        return importService.importCanonicalRows(datasetName(name, study), rows);
    }

    // ---- row building ------------------------------------------------------

    /** One canonical eval row from a completed interview, or {@code null} to skip. */
    private ObjectNode buildRow(TurResearchStudy study, Interview interview, String rubric) {
        ArrayNode turns = MAPPER.createArrayNode();
        StringBuilder answers = new StringBuilder();
        for (TurResearchTurnDto turn : interview.turns()) {
            if (StringUtils.isNotBlank(turn.question())) {
                turns.add(turn.question().trim());
            }
            if (StringUtils.isNotBlank(turn.answer())) {
                answers.append(turn.answer().trim()).append("\n\n");
            }
        }
        if (turns.isEmpty()) {
            // Nothing to replay — skip an interview with no interviewer questions.
            return null;
        }

        ObjectNode row = MAPPER.createObjectNode();
        row.put("name", interview.personaName());
        row.set("turns", turns);
        if (answers.length() > 0) {
            row.put("referenceAnswer",
                    StringUtils.abbreviate(answers.toString().trim(), MAX_REFERENCE_CHARS));
        }
        if (StringUtils.isNotBlank(rubric)) {
            row.put("rubric", rubric);
        }
        // A promoted row is a candidate harness: assert nothing (outcome ANY) until
        // a human curates it in the Eval Studio — same honesty as a mined failure.
        row.put("expectedOutcome", "ANY");

        ArrayNode tags = MAPPER.createArrayNode();
        tags.add("research-study");
        tags.add(study.getProtocol().name());
        row.set("tags", tags);

        ObjectNode metadata = MAPPER.createObjectNode();
        metadata.put("source", "research-study");
        metadata.put("studyId", study.getId());
        metadata.put("studyName", study.getName());
        metadata.put("personaId", interview.personaId());
        metadata.put("personaName", interview.personaName());
        row.set("metadata", metadata);

        return row;
    }

    /**
     * The study-level grader rubric: the goal + hypothesis, plus the T722
     * synthesized recommendations when a report is available. Fail-open — when no
     * LLM is configured the rubric degrades to the goal/hypothesis rather than
     * fabricating criteria.
     */
    private String buildRubric(TurResearchStudy study) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(study.getGoal())) {
            sb.append("Research goal: ").append(study.getGoal().trim()).append('\n');
        }
        if (StringUtils.isNotBlank(study.getHypothesis())) {
            sb.append("Hypothesis: ").append(study.getHypothesis().trim()).append('\n');
        }
        appendFindings(study.getId(), sb);
        return StringUtils.trimToNull(sb.toString());
    }

    private void appendFindings(String studyId, StringBuilder sb) {
        try {
            TurResearchReportDto report = insightsService.report(studyId, false);
            if (report == null || !report.available()) {
                return;
            }
            if (StringUtils.isNotBlank(report.executiveSummary())) {
                sb.append("Findings: ").append(report.executiveSummary().trim()).append('\n');
            }
            List<String> recommendations = report.recommendations();
            if (recommendations != null && !recommendations.isEmpty()) {
                sb.append("The agent's answers should align with these research recommendations:\n");
                for (String recommendation : recommendations) {
                    if (StringUtils.isNotBlank(recommendation)) {
                        sb.append("- ").append(recommendation.trim()).append('\n');
                    }
                }
            }
        } catch (RuntimeException e) {
            // Fail-open: synthesis is optional context, never a hard dependency.
            log.debug("[Research] eval-bridge findings unavailable for study={}: {}",
                    studyId, e.getMessage());
        }
    }

    // ---- interview loading (roster order) ----------------------------------

    /**
     * The study's COMPLETED interviews with captured turns, in roster (position)
     * order — mirrors {@link TurResearchInsightsService} so a promoted dataset lists
     * personas in the same order the study ran them.
     */
    private List<Interview> orderedCompletedInterviews(String studyId) {
        Map<String, TurResearchInterview> byPersona = new LinkedHashMap<>();
        for (TurResearchInterview interview :
                interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId)) {
            byPersona.put(interview.getPersonaId(), interview);
        }
        List<Interview> ordered = new ArrayList<>();
        for (TurResearchStudyPersona join :
                personaJoinRepository.findByStudy_IdOrderByPositionAsc(studyId)) {
            TurResearchInterview interview = byPersona.get(join.getPersonaId());
            if (interview == null
                    || interview.getStatus() != TurResearchInterviewStatus.COMPLETED) {
                continue;
            }
            List<TurResearchTurnDto> turns = parseTurns(interview.getTranscriptJson());
            if (turns.isEmpty()) {
                continue;
            }
            String name = personaRepository.findById(join.getPersonaId())
                    .map(TurPersona::getName).orElse(join.getPersonaId());
            ordered.add(new Interview(join.getPersonaId(), name, turns));
        }
        return ordered;
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

    private static String datasetName(String name, TurResearchStudy study) {
        if (StringUtils.isNotBlank(name)) {
            return name.trim();
        }
        return "research-" + study.getName();
    }

    // ---- internal interview view -------------------------------------------

    private record Interview(String personaId, String personaName,
            List<TurResearchTurnDto> turns) {
    }
}
