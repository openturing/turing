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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.ParseRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner.RankingIntent;

import lombok.extern.slf4j.Slf4j;

/**
 * T818 / §LIX.1 (Block BK) — the {@link TurCopilotPlanningStrategy#DETERMINISTIC}
 * planner: <strong>today's behaviour</strong>, lifted verbatim out of
 * {@code TurCatalogCopilotService.retrieve()} when planning became a seam.
 *
 * <p>The T811 ranking planner resolves any superlative / {@code sorted by} intent
 * deterministically (the LLM is unreliable at it), the ranking clause is
 * <em>stripped</em> from the text the LLM sees so the facet filter survives, and
 * the resolved sort + a bounded top-k size are injected afterwards. A sort-only
 * question (nothing left to facet) skips the LLM entirely and runs
 * {@code match_all} + sort.
 *
 * <p>Instant, single-LLM-call (zero when sort-only) and CI-testable — but the
 * ranking lexicon is English-only, which is what T819/T820 address.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurDeterministicCopilotQueryPlanner implements TurCopilotQueryPlanner {

    private final TurNLFacetParser parser;
    private final TurNLRankingPlanner rankingPlanner;

    public TurDeterministicCopilotQueryPlanner(TurNLFacetParser parser,
            TurNLRankingPlanner rankingPlanner) {
        this.parser = parser;
        this.rankingPlanner = rankingPlanner;
    }

    @Override
    public TurCopilotPlanningStrategy strategy() {
        return TurCopilotPlanningStrategy.DETERMINISTIC;
    }

    @Override
    public TurCopilotQueryPlan plan(PlanRequest request) {
        RankingIntent intent = rankingPlanner.detect(request.query(), request.schema());
        boolean sortOnly = intent.hasSort() && !StringUtils.hasText(intent.strippedQuery());
        Parsed parsed = sortOnly
                ? new Parsed(TurCopilotQueryBodies.matchAll(DEFAULT_TOP_K), false)
                : parseOrFallback(request, intent.strippedQuery());
        TurDslQueryRequest body = parsed.body();
        if (intent.hasSort()) {
            body = TurCopilotQueryBodies.withSort(body, intent.field(), intent.order(), DEFAULT_TOP_K);
        }
        String facetStep = facetStepNote(sortOnly, parsed.llmCall());
        String notes = "deterministic ranking=%s, %s".formatted(
                intent.hasSort() ? intent.field() + " " + intent.order() : "none", facetStep);
        return new TurCopilotQueryPlan(body, strategy(), parsed.llmCall() ? 1 : 0, notes);
    }

    /** How the faceting half of the plan was produced, for the observability trail. */
    private static String facetStepNote(boolean sortOnly, boolean llmCall) {
        if (sortOnly) {
            return "sort-only match_all (no LLM parse)";
        }
        return llmCall ? "LLM facet parse" : "free-text (no LLM available)";
    }

    /**
     * The LLM facet parse over the (ranking-stripped) query, degrading to a plain
     * full-text query when the parser is unavailable, the site declares no schema,
     * or the model output cannot be grounded. Reports whether an LLM call was actually
     * spent so the plan's cost is honest rather than assumed.
     */
    private Parsed parseOrFallback(PlanRequest request, String strippedQuery) {
        if (parser.isAvailable() && request.schema() != null && !request.schema().isEmpty()) {
            try {
                return new Parsed(parser.parse(new ParseRequest(request.siteName(), request.locale(),
                        strippedQuery, request.schema())), true);
            } catch (TurNLFacetParseException e) {
                log.warn("[CopilotPlanner] NL→facet parse failed for '{}': {} — falling back to free-text",
                        request.siteName(), e.getMessage());
                // The call was made and billed even though it produced nothing usable.
                return new Parsed(TurCopilotQueryBodies.freeText(strippedQuery, DEFAULT_TOP_K), true);
            }
        }
        return new Parsed(TurCopilotQueryBodies.freeText(strippedQuery, DEFAULT_TOP_K), false);
    }

    /** The parse outcome: the body plus whether it cost an LLM call. */
    private record Parsed(TurDslQueryRequest body, boolean llmCall) {
    }
}
