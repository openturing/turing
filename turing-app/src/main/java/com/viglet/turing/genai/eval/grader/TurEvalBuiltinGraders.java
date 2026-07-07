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

    /**
     * The legacy default stack. Order is cosmetic for the aggregate (mean is
     * order-independent and the overall pass is a logical AND) but kept stable
     * for deterministic reports.
     */
    public static final List<String> DEFAULT_STACK = List.of(SLOT_MATCH, OUTCOME, NODE, RUBRIC);

    private TurEvalBuiltinGraders() {
    }
}
