/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.time.LocalDateTime;

import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;

/**
 * T593 / §XXXIII.8 — API view of a {@link TurEvalReviewTask} for the review
 * inbox: the reviewer reads the transcript + expected summary and submits a
 * verdict.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalReviewTaskDto(
        String id,
        String agentId,
        String caseId,
        String caseName,
        String graderId,
        String transcript,
        String expectedSummary,
        String status,
        LocalDateTime createdAt,
        Boolean reviewedPass,
        Double reviewedScore,
        String reviewerNotes,
        String reviewedBy,
        LocalDateTime reviewedAt) {

    public static TurEvalReviewTaskDto fromEntity(TurEvalReviewTask t) {
        return new TurEvalReviewTaskDto(t.getId(), t.getAgentId(), t.getCaseId(), t.getCaseName(),
                t.getGraderId(), t.getTranscript(), t.getExpectedSummary(), t.getStatus(),
                t.getCreatedAt(), t.getReviewedPass(), t.getReviewedScore(), t.getReviewerNotes(),
                t.getReviewedBy(), t.getReviewedAt());
    }
}
