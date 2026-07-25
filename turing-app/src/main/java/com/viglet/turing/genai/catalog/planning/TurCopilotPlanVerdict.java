/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * T819 / §LIX.2 (Block BK) — the <strong>judge pass</strong> verdict: a narrow,
 * schema-bound answer to "does this structured query actually answer the question?".
 *
 * <p>Deliberately small. The judge is not asked to rewrite the query (that's the
 * refine pass); it is asked only to name the three failure modes the bug showed —
 * a dropped facet constraint, a missing/incorrect sort, a field the model invented —
 * plus the numeric field the question ranks on when it spotted one. Narrow questions
 * are what a cheap model answers reliably.
 *
 * @param valid           true when the query already answers the question as-is
 * @param missingFilters  constraints from the question the query failed to express
 * @param sortField       the field the question ranks on, or null/blank when none
 * @param sortOrder       {@code asc} / {@code desc} for {@link #sortField}
 * @param ungroundedField a field the query references that is not in the schema
 * @param rationale       one short sentence for the log / observability trail
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurCopilotPlanVerdict(
        boolean valid,
        List<String> missingFilters,
        String sortField,
        String sortOrder,
        String ungroundedField,
        String rationale) {

    /** True when the judge flagged anything worth a refine pass. */
    public boolean needsRefine() {
        return !valid
                || (missingFilters != null && !missingFilters.isEmpty())
                || (sortField != null && !sortField.isBlank())
                || (ungroundedField != null && !ungroundedField.isBlank());
    }
}
