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
 * T286 / §XV.2 — the scored result of replaying one
 * {@link com.viglet.turing.persistence.model.agent.TurAgentEvalCase} through
 * the chat-flow engine. Serialized into the {@code resultsJson} column of
 * {@link com.viglet.turing.persistence.model.agent.TurAgentEvalReport}.
 *
 * @param caseId          id of the source case
 * @param caseName        human label of the case
 * @param passed          true when every asserted dimension matched
 * @param score           per-case score in [0,1]
 * @param expectedOutcome expected terminal outcome ({@code ANY} = not asserted)
 * @param actualOutcome   the outcome observed after the replay
 * @param finalNodeId     the cursor node the replay landed on (nullable)
 * @param slotDiffs       per-slot expected-vs-actual comparison
 * @param rubricVerdict   {@code "pass"} / {@code "fail"} / {@code "na"} (no rubric)
 * @param rubricRationale the judge's one-line rationale (nullable)
 * @param error           non-null when the replay itself failed (engine /
 *                        LLM error); the case is then counted as failed
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurAgentEvalCaseResultDto(
        String caseId,
        String caseName,
        boolean passed,
        double score,
        String expectedOutcome,
        String actualOutcome,
        String finalNodeId,
        List<SlotDiff> slotDiffs,
        String rubricVerdict,
        String rubricRationale,
        String error) {

    /**
     * One expected-vs-actual slot comparison.
     *
     * @param slot     the slot name asserted
     * @param expected the expected value
     * @param actual   the captured value (nullable when never captured)
     * @param match    true when {@code actual} equals {@code expected}
     */
    public record SlotDiff(String slot, String expected, String actual, boolean match) {
    }
}
