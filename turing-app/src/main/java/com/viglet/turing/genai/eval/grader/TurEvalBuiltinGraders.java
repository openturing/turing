/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import java.util.List;

/**
 * T586 / §XXXIII.1 — ids of the built-in graders that reproduce the historical
 * four scoring dimensions, and the default stack order applied when a case /
 * set carries no explicit grader config (empty config ⇒ legacy behaviour).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEvalBuiltinGraders {

    /** CODE — deterministic slot-equality diff vs {@code expectedSlotsJson}. */
    public static final String SLOT_MATCH = "slot-match";

    /** CODE — terminal outcome label vs {@code expectedOutcome}. */
    public static final String OUTCOME = "outcome";

    /** CODE — final flow cursor vs {@code expectedNodeId}. */
    public static final String NODE = "node";

    /** MODEL — bilingual LLM judge over the natural-language {@code rubric}. */
    public static final String RUBRIC = "rubric";

    // ── T588 config-driven CODE library (opt-in; never in the default stack) ──

    /** CODE — final answer equals {@code expected} (ignoreCase / trim options). */
    public static final String EXACT = "exact";

    /** CODE — final answer contains {@code substring} (ignoreCase option). */
    public static final String CONTAINS = "contains";

    /** CODE — final answer matches the {@code pattern} regex (ignoreCase option). */
    public static final String REGEX = "regex";

    /** CODE — JSON-path {@code path} over the final answer, optional {@code expected}. */
    public static final String JSON_PATH = "json-path";

    /** CODE — a captured {@code slot} equals {@code expected} within {@code tolerance}. */
    public static final String SLOT_TOLERANCE = "slot-tolerance";

    /** CODE — a slot / answer number lies within {@code [min, max]}. */
    public static final String NUMERIC_RANGE = "numeric-range";

    /** CODE — the T427 trace shows {@code tool} called (optional {@code status} / {@code minCalls}). */
    public static final String TOOL_CALLED = "tool-called";

    /** CODE — aggregated tool-call latency (T427 durations) within {@code maxMs}. */
    public static final String LATENCY_BUDGET = "latency-budget";

    /** CODE — a custom Groovy script ({@code script}) returning score/pass/rationale (T589). */
    public static final String GROOVY = "groovy";

    /** MODEL — generalized LLM judge with LABEL/SCORE/CRITERIA/PAIRWISE strategies (T590). */
    public static final String MODEL_JUDGE = "model-judge";

    /** CODE — cosine similarity of answer vs a {@code reference}, via the embedding provider (T591). */
    public static final String EMBEDDING_SIMILARITY = "embedding-similarity";

    /** HUMAN — defers the case for human review (T592). */
    public static final String HUMAN_REVIEW = "human-review";

    /** CODE — Block R NL→facet scorer as a grader (T601): parses a query + scores vs a schema. */
    public static final String NL_FACET = "nl-facet";

    /**
     * The legacy default stack. Order is cosmetic for the aggregate (mean is
     * order-independent and the overall pass is a logical AND) but kept stable
     * for deterministic reports.
     */
    public static final List<String> DEFAULT_STACK = List.of(SLOT_MATCH, OUTCOME, NODE, RUBRIC);

    private TurEvalBuiltinGraders() {
    }
}
