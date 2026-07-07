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
import java.util.List;

import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;

/**
 * T593 / §XXXIII.8 — API view of a {@link TurEvalReviewTask} for the review
 * inbox: the reviewer reads the transcript + expected summary and submits a
 * verdict. T594 adds the calibration/agreement fields: the task type
 * (DEFERRED vs AUDIT), how many reviewers are required, how many have decided so
 * far, the model's original verdict (for AUDIT tasks), and the individual
 * verdicts (detail view only).
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
        LocalDateTime reviewedAt,
        String taskType,
        int requiredReviewers,
        int verdictCount,
        Boolean modelPass,
        Double modelScore,
        List<TurEvalReviewVerdictDto> verdicts) {

    /** List view: no individual verdicts loaded, {@code verdictCount} supplied. */
    public static TurEvalReviewTaskDto fromEntity(TurEvalReviewTask t, int verdictCount) {
        return new TurEvalReviewTaskDto(t.getId(), t.getAgentId(), t.getCaseId(), t.getCaseName(),
                t.getGraderId(), t.getTranscript(), t.getExpectedSummary(), t.getStatus(),
                t.getCreatedAt(), t.getReviewedPass(), t.getReviewedScore(), t.getReviewerNotes(),
                t.getReviewedBy(), t.getReviewedAt(), t.getTaskType(), t.getRequiredReviewers(),
                verdictCount, t.getModelPass(), t.getModelScore(), null);
    }

    /** Detail view with the individual verdicts attached. */
    public static TurEvalReviewTaskDto fromEntity(TurEvalReviewTask t,
            List<TurEvalReviewVerdictDto> verdicts) {
        int count = verdicts == null ? 0 : verdicts.size();
        return new TurEvalReviewTaskDto(t.getId(), t.getAgentId(), t.getCaseId(), t.getCaseName(),
                t.getGraderId(), t.getTranscript(), t.getExpectedSummary(), t.getStatus(),
                t.getCreatedAt(), t.getReviewedPass(), t.getReviewedScore(), t.getReviewerNotes(),
                t.getReviewedBy(), t.getReviewedAt(), t.getTaskType(), t.getRequiredReviewers(),
                count, t.getModelPass(), t.getModelScore(), verdicts);
    }

    /** Back-compat single-arg factory (verdict count unknown). */
    public static TurEvalReviewTaskDto fromEntity(TurEvalReviewTask t) {
        return fromEntity(t, 0);
    }
}
