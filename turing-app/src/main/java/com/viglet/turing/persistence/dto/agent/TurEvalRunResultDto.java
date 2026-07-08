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

/**
 * T602 / §XXXIII.17 — result of a public {@code dataset × grader stack} run.
 * Wraps the persisted {@link TurAgentEvalReportDto} with the resolved bindings
 * and the final <b>gate</b> verdict a CI pipeline keys its exit code on:
 * {@code gatePassed = report.passed && (minScore == null || report.score >= minScore)}.
 *
 * @param gatePassed  the CI verdict (every case passed AND, if set, score ≥ minScore).
 * @param minScore    the optional score threshold that was applied (echoed back).
 * @param datasetId   the resolved dataset id the run scored.
 * @param datasetName the resolved dataset's human name.
 * @param graderStackId the resolved grader-stack id ({@code null} = default stack).
 * @param report      the full per-case report (persisted for the Eval Studio timeline).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalRunResultDto(
        boolean gatePassed,
        Double minScore,
        String datasetId,
        String datasetName,
        String graderStackId,
        TurAgentEvalReportDto report) {
}
