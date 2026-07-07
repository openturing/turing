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

/**
 * T286 / §XV.2 — API view of a {@link
 * com.viglet.turing.persistence.model.agent.TurAgentEvalReport} with the
 * per-case breakdown already parsed out of {@code resultsJson}.
 *
 * @param reportId    id of the persisted report ({@code null} when no eval set
 *                    exists / nothing ran)
 * @param createdAt   when the run finished
 * @param passed      true when every case passed
 * @param score       aggregate score in [0,1]
 * @param caseCount   total cases run
 * @param passedCount cases that passed
 * @param baseline    true when this report is the agent's green baseline
 * @param regressed   true when this run regressed against the baseline
 * @param pendingReview T592 — true when any case deferred to a human grader
 * @param results     per-case results
 * @param error       non-null when the run could not start (no enabled set,
 *                    no usable LLM, etc.)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurAgentEvalReportDto(
        String reportId,
        LocalDateTime createdAt,
        boolean passed,
        double score,
        int caseCount,
        int passedCount,
        boolean baseline,
        boolean regressed,
        boolean pendingReview,
        String datasetId,
        int datasetVersion,
        List<TurAgentEvalCaseResultDto> results,
        String error) {

    /** T592 constructor (no dataset pin); {@code datasetId=null}, {@code datasetVersion=0}. */
    public TurAgentEvalReportDto(String reportId, LocalDateTime createdAt, boolean passed, double score,
            int caseCount, int passedCount, boolean baseline, boolean regressed, boolean pendingReview,
            List<TurAgentEvalCaseResultDto> results, String error) {
        this(reportId, createdAt, passed, score, caseCount, passedCount, baseline, regressed,
                pendingReview, null, 0, results, error);
    }

    /** Legacy constructor (no human review); {@code pendingReview=false}. */
    public TurAgentEvalReportDto(String reportId, LocalDateTime createdAt, boolean passed, double score,
            int caseCount, int passedCount, boolean baseline, boolean regressed,
            List<TurAgentEvalCaseResultDto> results, String error) {
        this(reportId, createdAt, passed, score, caseCount, passedCount, baseline, regressed, false,
                results, error);
    }

    public static TurAgentEvalReportDto error(String error) {
        return new TurAgentEvalReportDto(null, null, false, 0d, 0, 0, false, false, List.of(), error);
    }
}
