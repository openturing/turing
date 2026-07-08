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
 * T602 / §XXXIII.17 — request body for the public "run a named {@code dataset ×
 * grader stack} remotely" endpoint ({@code POST /api/eval/run}).
 *
 * <p>{@code dataset} and {@code graderStack} accept <b>either</b> an id or a
 * human name (the endpoint resolves id-first, then falls back to name) so a CI
 * pipeline can reference a dataset by its stable name without pinning an id.
 *
 * @param agentId     the agent runtime to replay the dataset against (required).
 * @param dataset     dataset id or name to score (required).
 * @param graderStack grader-stack id or name; blank / null runs the default
 *                    stack (slot / outcome / node / rubric).
 * @param minScore    optional extra gate: when set, the run only passes if its
 *                    aggregate score is {@code >= minScore} (on top of the
 *                    every-case-passes rule).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalRunRequest(
        String agentId,
        String dataset,
        String graderStack,
        Double minScore) {
}
