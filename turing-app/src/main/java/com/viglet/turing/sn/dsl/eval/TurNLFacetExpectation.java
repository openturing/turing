/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The golden expected structured output for one NL→facet eval case (T385 /
 * §XX.5): which facet filters, ranges and sort the parser must produce for a
 * prose query, over the declared field schema.
 *
 * <p>Matching is intentionally <b>tolerant</b> (see {@link TurNLFacetEvalScorer}):
 * a case asserts that the expected clauses are <i>present</i>, not that the query
 * is byte-for-byte equal — an LLM may legitimately add a full-text {@code match}
 * clause or reorder a {@code bool}. What it must <i>not</i> do is filter on a
 * field outside the declared schema; that is the objective-grounding invariant
 * Block&nbsp;R pins, and the scorer flags it as a hard failure.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurNLFacetExpectation(
        List<ExpectedFilter> filters,
        List<ExpectedRange> ranges,
        ExpectedSort sort) {

    public TurNLFacetExpectation {
        filters = filters == null ? List.of() : filters;
        ranges = ranges == null ? List.of() : ranges;
    }

    /** A facet/term equality the parser must emit (term/terms or a match on the field). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpectedFilter(String field, String value) {
    }

    /**
     * A numeric range the parser must emit on {@code field}. Only the bounds that
     * are non-null are checked; an expected upper bound matches an actual
     * {@code lte} <i>or</i> {@code lt} and a lower bound matches {@code gte} or
     * {@code gt} (direction + approximate value), so "under 20000" passes whether
     * the model chose {@code lte} or {@code lt}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpectedRange(String field, Double gte, Double gt, Double lte, Double lt) {
    }

    /** Expected primary sort: a field and an order ({@code asc} / {@code desc}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpectedSort(String field, String order) {
    }
}
