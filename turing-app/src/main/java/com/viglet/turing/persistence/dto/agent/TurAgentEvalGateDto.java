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

import java.util.List;

/**
 * T287 / §XV.3 — pre-publish gate status surfaced in the flow editor's Lint
 * UI panel. Computed from the agent's latest eval {@link
 * com.viglet.turing.persistence.model.agent.TurAgentEvalReport} compared to
 * its green baseline. The findings mirror the {@link TurChatFlowLintIssueDto}
 * shape so the frontend can render them with the same affordance.
 *
 * @param status    one of {@code NOT_CONFIGURED} (no enabled set), {@code
 *                  NEVER_RUN} (set exists, no report yet), {@code GREEN}
 *                  (latest run passed), {@code REGRESSED} (latest run broke a
 *                  baseline-green case), {@code RED} (failing, no regression
 *                  baseline to compare)
 * @param blocking  true when at least one enabled set is marked blocking — a
 *                  RED/REGRESSED gate should hard-block publish
 * @param lastReport the latest run report ({@code null} when NEVER_RUN /
 *                  NOT_CONFIGURED)
 * @param findings  one row per problem, newest-/loudest-first
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurAgentEvalGateDto(
        String status,
        boolean blocking,
        TurAgentEvalReportDto lastReport,
        List<Finding> findings) {

    /**
     * One gate finding, shaped like a lint issue.
     *
     * @param severity {@code "ERROR"} / {@code "WARNING"} / {@code "INFO"}
     * @param code     stable machine-readable id (e.g. {@code eval_regression})
     * @param message  one-line human description
     * @param hint     one-line actionable suggestion
     */
    public record Finding(String severity, String code, String message, String hint) {
    }

    public static TurAgentEvalGateDto notConfigured() {
        return new TurAgentEvalGateDto("NOT_CONFIGURED", false, null, List.of());
    }
}
