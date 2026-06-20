/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;

/**
 * Request body for one golden case inside a {@code TurAgentEvalSet} upsert.
 * Mirrors the client-editable fields of {@code TurAgentEvalCase}; the
 * {@code id}, {@code sortOrder} and the parent back-reference are assigned by
 * the server on save, so the persistent entity is never bound directly from the
 * HTTP request. JSON field names match the entity's, so the editor contract is
 * unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurAgentEvalCaseRequest(
        String name,
        String description,
        String seedTurnsJson,
        String expectedSlotsJson,
        TurAgentEvalExpectedOutcome expectedOutcome,
        String expectedNodeId,
        String rubric) {
}
