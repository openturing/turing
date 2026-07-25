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
 * T819 / §LIX.2 (Block BK) — the <strong>refine pass</strong> output: the query the
 * model intends, expressed as a flat list of grounded constraints rather than as a
 * hand-written Query DSL body.
 *
 * <p>This is the point of the multi-pass design. Asking a cheap model to "produce the
 * whole DSL" is exactly what fails (truncated JSON, dropped commas, a superlative
 * stuffed into a term filter). Asking it instead for <em>which declared field equals
 * which value</em>, <em>which field is a range</em> and <em>which numeric field the
 * ranking is on</em> is a narrow question it answers reliably — and
 * {@link TurCopilotPlanCompiler} turns that into a valid DSL body deterministically,
 * dropping anything ungrounded on the way.
 *
 * @param termFilters  exact facet matches ({@code field = value})
 * @param rangeFilters numeric comparisons ({@code gte} / {@code lte})
 * @param freeText     residual full-text intent that maps to no declared field
 * @param sortField    declared numeric field the ranking is on (null when none)
 * @param sortOrder    {@code asc} / {@code desc} for {@link #sortField}
 * @param rationale    one short sentence for the log / observability trail
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurCopilotPlanRepair(
        List<TermFilter> termFilters,
        List<RangeFilter> rangeFilters,
        String freeText,
        String sortField,
        String sortOrder,
        String rationale) {

    /** An exact facet match on a declared field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TermFilter(String field, String value) {
    }

    /**
     * A numeric comparison on a declared field. Both bounds are optional
     * ({@code gte} only = "at least", {@code lte} only = "at most", both = between).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RangeFilter(String field, Double gte, Double lte) {
    }
}
