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
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalPack;

/**
 * T821 / §LIX.4 (Block BK) — what to compare: an NL→facet eval pack plus the
 * optional knobs of the run.
 *
 * <p>Both knobs default to "everything, as configured": an empty {@code strategies}
 * list compares all three, and a null {@code maxPasses} uses the deployment-wide
 * {@code turing.genai.copilot.planning.max-passes}. Restricting the strategy set is
 * what makes the comparison affordable — an {@code LLM_ASSISTED} row costs up to
 * three LLM calls <em>per case</em>.
 *
 * @param pack       the eval pack to run through every strategy
 * @param strategies which strategies to compare; empty ⇒ all of them
 * @param maxPasses  analysis depth for the LLM strategies; null ⇒ the configured default
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurCopilotPlanningComparisonRequest(
        TurNLFacetEvalPack pack,
        List<TurCopilotPlanningStrategy> strategies,
        Integer maxPasses) {

    public TurCopilotPlanningComparisonRequest {
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    /** Compare every strategy at the configured depth. */
    public static TurCopilotPlanningComparisonRequest of(TurNLFacetEvalPack pack) {
        return new TurCopilotPlanningComparisonRequest(pack, List.of(), null);
    }
}
