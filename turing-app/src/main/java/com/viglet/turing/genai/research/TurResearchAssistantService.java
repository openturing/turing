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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Research Assistant — guided study authoring (Block AW / §XLVI.5, T732). A
 * skippable conversational guide that turns a free-text "what do you want to
 * learn?" into a structured study **proposal** (name / goal / hypothesis /
 * protocol + protocol payload / a suggested audience brief for T731), so a
 * researcher doesn't face an empty protocol editor. Reuses the AI-authoring
 * strict-schema discipline (the T731 cohort + persona-from-audio pattern) over
 * {@link TurLlmSummaryService} — one call, defensively parsed, <strong>never
 * auto-applied</strong>: the studio pre-fills the create form with the proposal
 * and the human edits/creates.
 *
 * <p>Fail-open — {@code success=false} with an {@code error} when there is no
 * default LLM or the output can't be parsed; nothing is persisted.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchAssistantService {

    private static final int DEFAULT_PARTICIPANTS = 5;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String SYSTEM_PROMPT = """
            You are a UX research assistant. A researcher tells you, in free text,
            what they want to learn. Propose ONE synthetic-user research study.

            Return EXACTLY ONE JSON object — no prose, no code fences. Schema:
            {"name": "<short study name>",
             "goal": "<one sentence: what the study learns>",
             "hypothesis": "<the expectation to probe, or empty>",
             "protocol": "DYNAMIC_SCRIPT|CUSTOM_SCRIPT|CONCEPT_TEST",
             "conceptText": "<the concept to react to — ONLY for CONCEPT_TEST, else empty>",
             "questions": ["<question>", "..."],
             "maxQuestions": <3-12>,
             "suggestedCohortBrief": "<one paragraph describing a diverse audience to interview>",
             "suggestedParticipantCount": <3-8>,
             "assistantMessage": "<2-3 friendly sentences summarising the proposed study for the researcher>"}

            Choose the protocol that fits the goal: DYNAMIC_SCRIPT for open
            discovery (goal-driven adaptive follow-ups; leave questions empty),
            CUSTOM_SCRIPT when the researcher implies specific questions (fill
            questions), CONCEPT_TEST when they want reactions to a specific concept
            (fill conceptText). Write every field in the researcher's language.
            Ground everything in what they said; invent no facts.
            """;

    private final TurLlmSummaryService llmSummaryService;

    public TurResearchAssistantService(TurLlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    /** The proposed study configuration (never auto-applied). */
    public record StudyProposal(String name, String goal, String hypothesis, String protocol,
            String conceptText, List<String> questions, int maxQuestions,
            String suggestedCohortBrief, int suggestedParticipantCount,
            String assistantMessage) {
    }

    /** Wire response: the proposal, or a fail-open error. */
    public record ProposalResult(boolean success, String error, StudyProposal proposal) {

        public static ProposalResult failed(String error) {
            return new ProposalResult(false, error, null);
        }
    }

    public ProposalResult propose(String brief, boolean regenerate) {
        if (StringUtils.isBlank(brief)) {
            return ProposalResult.failed("Tell the assistant what you want to learn.");
        }
        String hash = Integer.toHexString(brief.trim().hashCode());
        SummaryResult result = llmSummaryService.generate(
                "research-assistant:" + hash, brief.trim(), SYSTEM_PROMPT, regenerate);
        if (!result.success() || StringUtils.isBlank(result.content())) {
            return ProposalResult.failed(Objects.toString(result.error(),
                    "Could not draft a study (no default LLM?)."));
        }
        ProposalDto dto = parse(result.content());
        if (dto == null) {
            return ProposalResult.failed("Could not parse the drafted study.");
        }
        return new ProposalResult(true, null, toProposal(dto));
    }

    private StudyProposal toProposal(ProposalDto dto) {
        return new StudyProposal(
                StringUtils.trimToEmpty(dto.name()),
                StringUtils.trimToEmpty(dto.goal()),
                StringUtils.trimToEmpty(dto.hypothesis()),
                normalizeProtocol(dto.protocol()),
                StringUtils.trimToEmpty(dto.conceptText()),
                dto.questions() == null ? List.of() : dto.questions(),
                dto.maxQuestions() == null ? 6 : Math.clamp(dto.maxQuestions(), 1, 30),
                StringUtils.trimToEmpty(dto.suggestedCohortBrief()),
                dto.suggestedParticipantCount() == null ? DEFAULT_PARTICIPANTS
                        : Math.clamp(dto.suggestedParticipantCount(), 1, 12),
                StringUtils.trimToEmpty(dto.assistantMessage()));
    }

    /** Coerce the model's protocol to a valid enum name, defaulting to DYNAMIC_SCRIPT. */
    private String normalizeProtocol(String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                return TurResearchProtocol.valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                log.warn("[ResearchAssistant] unknown protocol '{}', defaulting to DYNAMIC_SCRIPT", value);
            }
        }
        return TurResearchProtocol.DYNAMIC_SCRIPT.name();
    }

    private ProposalDto parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readValue(raw.substring(start, end + 1), ProposalDto.class);
        } catch (RuntimeException e) {
            log.warn("[ResearchAssistant] could not parse proposal: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProposalDto(String name, String goal, String hypothesis, String protocol,
            String conceptText, List<String> questions, Integer maxQuestions,
            String suggestedCohortBrief, Integer suggestedParticipantCount,
            String assistantMessage) {
    }
}
