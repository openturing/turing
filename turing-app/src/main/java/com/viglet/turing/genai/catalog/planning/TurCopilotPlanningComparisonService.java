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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.catalog.planning.TurCopilotPlanningComparison.StrategyOutcome;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.properties.TurCatalogCopilotProperty;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalCase;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalPack;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalScorer;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;

import lombok.extern.slf4j.Slf4j;

/**
 * T821 / §LIX.4 (Block BK) — runs one NL→facet eval pack through every
 * {@link TurCopilotQueryPlanner} and reports quality, latency and LLM cost side by
 * side, so a deployment can see what switching its copilot's planning strategy
 * would buy <em>its</em> catalog before switching.
 *
 * <p>It lives on the planning side rather than inside {@code TurNLFacetEvalService}
 * on purpose: {@code genai.catalog.planning} already depends on {@code sn.dsl.eval}
 * (the planners drive the NL→facet parser), so putting the comparison in the eval
 * package would close that into a cycle. The dependency stays one-way — this
 * service <em>reuses</em> the eval pack, its schema resolution and its scorer, and
 * the eval package knows nothing about planning.
 *
 * <h2>What the numbers do and do not prove</h2>
 *
 * The comparison is <strong>plan-only</strong>: it scores the query each strategy
 * <em>plans</em>, never one it executes. That is honest for
 * {@link TurCopilotPlanningStrategy#DETERMINISTIC} and
 * {@link TurCopilotPlanningStrategy#LLM_ASSISTED}, whose whole behaviour is in
 * {@code plan(...)}, but not for {@link TurCopilotPlanningStrategy#HYBRID}, whose
 * escalation lives in {@code replan(...)} and only fires on a live retrieval result
 * (zero hits, or a degenerate plan on a specific question). A plan-only run cannot
 * produce that signal, so HYBRID necessarily scores <em>identically</em> to
 * DETERMINISTIC here. Rather than let the table imply the two are equivalent in
 * production, that limit is stated in the report's caveats and on the HYBRID row
 * itself — measuring the escalation for real needs a live index, which makes it an
 * integration test rather than a CI-cheap scorer.
 *
 * <p>Like the eval runner this is intentionally not {@code @Transactional}: a full
 * comparison makes up to three LLM calls per case <em>per strategy</em> and can take
 * minutes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCopilotPlanningComparisonService {

    /** Stated on every real run — the numbers are planned queries, not executed ones. */
    static final String CAVEAT_PLAN_ONLY =
            "Plan-only: each strategy's planned query is scored against the pack's golden "
                    + "clauses, never executed against the index. Retrieval quality (does the "
                    + "query actually return the right documents?) is not measured here.";

    /** Stated whenever HYBRID is part of the run — its escalation cannot fire plan-only. */
    static final String CAVEAT_HYBRID =
            "HYBRID scores identically to DETERMINISTIC by construction: its escalation only "
                    + "fires on live retrieval (zero hits, or a degenerate plan on a specific "
                    + "question), which a plan-only comparison cannot produce. These numbers "
                    + "understate HYBRID — they are its fast-path, not its behaviour in production.";

    /** Stated on every real run — wall-clock is dominated by live LLM latency. */
    static final String CAVEAT_LATENCY =
            "Elapsed time is real wall-clock against the configured LLM and varies run to run "
                    + "with model, provider load and network. Compare orders of magnitude, not "
                    + "milliseconds.";

    private final TurNLFacetEvalService evalService;
    private final TurNLFacetEvalScorer scorer;
    private final TurCatalogCopilotProperty copilotProperty;
    private final Map<TurCopilotPlanningStrategy, TurCopilotQueryPlanner> planners =
            new EnumMap<>(TurCopilotPlanningStrategy.class);

    public TurCopilotPlanningComparisonService(TurNLFacetEvalService evalService,
            TurNLFacetEvalScorer scorer, TurCatalogCopilotProperty copilotProperty,
            List<TurCopilotQueryPlanner> allPlanners) {
        this.evalService = evalService;
        this.scorer = scorer;
        this.copilotProperty = copilotProperty;
        for (TurCopilotQueryPlanner planner : allPlanners) {
            planners.put(planner.strategy(), planner);
        }
    }

    /** True when the comparison can run (the planners need a usable default LLM). */
    public boolean isAvailable() {
        return evalService.isAvailable();
    }

    /**
     * Run {@code request.pack()} through each requested strategy and aggregate the
     * side-by-side report. A strategy that blows up on a case scores that case as a
     * failure rather than aborting the whole comparison.
     */
    public TurCopilotPlanningComparison compare(TurCopilotPlanningComparisonRequest request) {
        TurNLFacetEvalPack pack = request == null ? null : request.pack();
        if (pack == null) {
            return TurCopilotPlanningComparison.error("(null)", "No eval pack provided");
        }
        if (pack.cases().isEmpty()) {
            return TurCopilotPlanningComparison.error(pack.name(), "Eval pack has no cases");
        }
        if (!evalService.isAvailable()) {
            return TurCopilotPlanningComparison.error(pack.name(),
                    "No usable default LLM configured for copilot query planning");
        }

        List<TurNLFacetField> schema = evalService.resolveSchema(pack);
        if (schema.isEmpty()) {
            return TurCopilotPlanningComparison.error(pack.name(),
                    "No field schema (pack declares none and site '%s' has no fields)"
                            .formatted(pack.index()));
        }

        int depth = resolveDepth(request.maxPasses());
        Set<TurCopilotPlanningStrategy> strategies = resolveStrategies(request.strategies());

        List<StrategyOutcome> outcomes = new ArrayList<>();
        for (TurCopilotPlanningStrategy strategy : strategies) {
            TurCopilotQueryPlanner planner = planners.get(strategy);
            if (planner == null) {
                log.warn("[CopilotPlanningEval] no planner registered for {} — skipping", strategy);
                continue;
            }
            outcomes.add(runStrategy(planner, pack, schema, depth));
        }
        return new TurCopilotPlanningComparison(pack.name(), pack.cases().size(), depth, outcomes,
                caveatsFor(strategies), null);
    }

    // ─────────────────────────── Per-strategy ───────────────────────────

    private StrategyOutcome runStrategy(TurCopilotQueryPlanner planner, TurNLFacetEvalPack pack,
            List<TurNLFacetField> schema, int depth) {
        TurCopilotPlanningStrategy strategy = planner.strategy();
        List<CaseResult> results = new ArrayList<>();
        int llmPasses = 0;
        long startNanos = System.nanoTime();
        for (TurNLFacetEvalCase evalCase : pack.cases()) {
            Scored scored = runCase(planner, pack, evalCase, schema, depth);
            results.add(scored.result());
            llmPasses += scored.llmPasses();
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        int passedCount = (int) results.stream().filter(CaseResult::passed).count();
        double score = results.stream().mapToDouble(CaseResult::score).average().orElse(0d);
        log.info("[CopilotPlanningEval] pack '{}' strategy {} (depth {}): {}/{} passed, "
                + "score {}, {} LLM pass(es), {} ms", pack.name(), strategy, depth, passedCount,
                results.size(), String.format("%.2f", score), llmPasses, elapsedMillis);
        return new StrategyOutcome(strategy, passedCount == results.size(), passedCount, score,
                llmPasses, elapsedMillis, noteFor(strategy, depth), results);
    }

    /**
     * Plan one case and score the planned query. Planners are contractually
     * fail-open, but a thrown exception is still caught here so one bad case can
     * never lose the rest of the comparison.
     */
    private Scored runCase(TurCopilotQueryPlanner planner, TurNLFacetEvalPack pack,
            TurNLFacetEvalCase evalCase, List<TurNLFacetField> schema, int depth) {
        try {
            TurCopilotQueryPlan plan = planner.plan(new PlanRequest(pack.index(), pack.locale(),
                    evalCase.query(), schema, depth));
            if (plan == null) {
                return new Scored(failed(evalCase, "planner returned no plan"), 0);
            }
            return new Scored(scorer.score(evalCase.name(), evalCase.expect(), plan.request(), schema),
                    plan.llmPasses());
        } catch (RuntimeException e) {
            log.warn("[CopilotPlanningEval] {} failed on case '{}': {}", planner.strategy(),
                    evalCase.name(), e.getMessage());
            return new Scored(failed(evalCase, "planning failed: " + e.getMessage()), 0);
        }
    }

    private static CaseResult failed(TurNLFacetEvalCase evalCase, String reason) {
        return new CaseResult(evalCase.name(), false, 0d, List.of(reason), List.of(), reason);
    }

    /** One scored case plus what it cost. */
    private record Scored(CaseResult result, int llmPasses) {
    }

    // ─────────────────────────── Resolution & prose ───────────────────────────

    /** The requested depth, else the deployment-wide default; never negative. */
    private int resolveDepth(Integer requested) {
        int depth = requested != null ? requested : copilotProperty.getPlanning().getMaxPasses();
        return Math.max(0, depth);
    }

    /** The requested strategies in enum order, or all of them when none were named. */
    private static Set<TurCopilotPlanningStrategy> resolveStrategies(
            List<TurCopilotPlanningStrategy> requested) {
        if (requested == null || requested.isEmpty()) {
            return new LinkedHashSet<>(List.of(TurCopilotPlanningStrategy.values()));
        }
        Set<TurCopilotPlanningStrategy> unique = new LinkedHashSet<>();
        for (TurCopilotPlanningStrategy strategy : TurCopilotPlanningStrategy.values()) {
            if (requested.contains(strategy)) {
                unique.add(strategy);
            }
        }
        return unique;
    }

    /** What the numbers do not prove — always present, HYBRID's limit only when it ran. */
    private static List<String> caveatsFor(Set<TurCopilotPlanningStrategy> strategies) {
        List<String> caveats = new ArrayList<>();
        caveats.add(CAVEAT_PLAN_ONLY);
        if (strategies.contains(TurCopilotPlanningStrategy.HYBRID)) {
            caveats.add(CAVEAT_HYBRID);
        }
        caveats.add(CAVEAT_LATENCY);
        return caveats;
    }

    /** How to read one row: the depth that applied, and HYBRID's plan-only equivalence. */
    private static String noteFor(TurCopilotPlanningStrategy strategy, int depth) {
        return switch (strategy) {
            case DETERMINISTIC -> "single facet parse (depth does not apply); "
                    + "zero LLM calls on a sort-only question";
            case LLM_ASSISTED -> "depth %d — %s".formatted(depth, depthLabel(depth));
            case HYBRID -> "fast-path only: identical to DETERMINISTIC here because escalation "
                    + "needs a live retrieval result";
        };
    }

    private static String depthLabel(int depth) {
        return switch (Math.min(depth, 2)) {
            case 0 -> "parse only";
            case 1 -> "parse + judge";
            default -> "parse + judge + refine";
        };
    }
}
