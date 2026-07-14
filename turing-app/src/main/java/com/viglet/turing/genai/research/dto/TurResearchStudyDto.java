/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research.dto;

import java.time.Instant;
import java.util.List;

/**
 * The API view of a {@link com.viglet.turing.persistence.model.research.TurResearchStudy}
 * (Block AW / §XLVI.2, T719) — doubles as the create/update request body (id +
 * derived counts / {@code llmName} / {@code lastRunAt} / roster / interviews are
 * ignored on write). {@code questions} is the CUSTOM_SCRIPT list; {@code conceptText}
 * the CONCEPT_TEST stimulus; {@code maxQuestions} the DYNAMIC_SCRIPT follow-up cap.
 * {@code targetAgentId} (T726) points the study at a live agent — the persona then
 * plays the synthetic user testing it; {@code targetAgentName} is the resolved
 * label (read-only, ignored on write).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchStudyDto(
        String id,
        String name,
        String goal,
        String hypothesis,
        String description,
        boolean enabled,
        String protocol,
        String conceptText,
        List<String> questions,
        int maxQuestions,
        String llmInstanceId,
        String llmName,
        String targetAgentId,
        String targetAgentName,
        String interviewLlmInstanceId,
        String synthesisLlmInstanceId,
        String schedule,
        Instant lastRunAt,
        int personaCount,
        int interviewCount,
        List<TurResearchStudyPersonaRefDto> personas,
        List<TurResearchInterviewDto> interviews) {
}
