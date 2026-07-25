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

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.ParseRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;

import lombok.extern.slf4j.Slf4j;

/**
 * T819 / §LIX.2 (Block BK) — the {@link TurCopilotPlanningStrategy#LLM_ASSISTED}
 * planner: <strong>parse → judge → refine</strong>.
 *
 * <p>The premise is that one "produce the whole DSL" call is what fails on a cheap
 * model, so the work is decomposed into narrow, verifiable steps:
 * <ol>
 *   <li><b>parse</b> — the T385 NL→facet parse over the <em>full</em> question (no
 *       ranking clause stripped: the LLM path handles superlatives and other
 *       languages natively, which is where its i18n comes from).</li>
 *   <li><b>judge</b> — {@link TurCopilotPlanJudge#judge} audits the parsed query
 *       against the question: dropped facet? missing/incorrect sort? ungrounded
 *       field? A narrow question a cheap model answers reliably.</li>
 *   <li><b>refine</b> — when the judge flagged something,
 *       {@link TurCopilotPlanJudge#refine} restates the question as a flat list of
 *       constraints, which {@link TurCopilotPlanCompiler} compiles into a grounded
 *       query deterministically (dropping anything the model invented).</li>
 * </ol>
 *
 * <p>Depth is a parameter: {@code maxPasses} {@code 0} = parse only, {@code 1} =
 * + judge, {@code 2} = + refine — so a deployment dials cost against quality.
 *
 * <p><strong>Fail-open at every step</strong>: a failed parse degrades to a free-text
 * query, a failed judge keeps the parse, and a refine that yields nothing grounded
 * keeps the judged plan. The planner never throws and never returns a query the
 * schema doesn't ground.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLlmAssistedCopilotQueryPlanner implements TurCopilotQueryPlanner {

    /** Upper bound on {@code maxPasses}: parse (+1 judge) (+1 refine). */
    static final int MAX_SUPPORTED_PASSES = 2;

    private final TurNLFacetParser parser;
    private final TurCopilotPlanJudge judge;
    private final TurCopilotPlanCompiler compiler;
    private final TurCopilotPlanValidator validator;

    public TurLlmAssistedCopilotQueryPlanner(TurNLFacetParser parser, TurCopilotPlanJudge judge,
            TurCopilotPlanCompiler compiler, TurCopilotPlanValidator validator) {
        this.parser = parser;
        this.judge = judge;
        this.compiler = compiler;
        this.validator = validator;
    }

    @Override
    public TurCopilotPlanningStrategy strategy() {
        return TurCopilotPlanningStrategy.LLM_ASSISTED;
    }

    @Override
    public TurCopilotQueryPlan plan(PlanRequest request) {
        return plan(request, strategy());
    }

    /**
     * The multi-pass plan, labelled under {@code label} so the T820 HYBRID planner can
     * reuse it verbatim while still reporting itself as {@code HYBRID}.
     */
    TurCopilotQueryPlan plan(PlanRequest request, TurCopilotPlanningStrategy label) {
        Parsed parsed = parse(request);
        TurDslQueryRequest body = TurCopilotQueryBodies.withBoundedSize(parsed.body(), DEFAULT_TOP_K);
        int passes = parsed.llmCall() ? 1 : 0;
        int depth = clampDepth(request.maxPasses());
        if (depth < 1) {
            return new TurCopilotQueryPlan(body, label, passes, "parse-only (depth 0)");
        }

        Optional<TurCopilotPlanVerdict> verdictOpt = judge.judge(request, body);
        if (verdictOpt.isEmpty()) {
            return new TurCopilotQueryPlan(body, label, passes, "parse; judge unavailable");
        }
        passes++;
        TurCopilotPlanVerdict verdict = verdictOpt.get();
        if (!verdict.needsRefine()) {
            return new TurCopilotQueryPlan(body, label, passes, "parse+judge (query accepted)");
        }
        if (depth < MAX_SUPPORTED_PASSES) {
            // Judged but refine is out of budget. Still apply the one repair we can do
            // deterministically from the verdict alone: the sort the judge named.
            TurDslQueryRequest sorted = applyJudgedSort(body, request, verdict);
            return new TurCopilotQueryPlan(sorted, label, passes,
                    "parse+judge (depth 1; sort repaired only): " + note(verdict));
        }

        Optional<TurCopilotPlanRepair> repairOpt = judge.refine(request, body, verdict);
        if (repairOpt.isEmpty()) {
            TurDslQueryRequest sorted = applyJudgedSort(body, request, verdict);
            return new TurCopilotQueryPlan(sorted, label, passes,
                    "parse+judge (refine unavailable): " + note(verdict));
        }
        passes++;
        Optional<TurDslQueryRequest> compiled =
                compiler.compile(repairOpt.get(), request.schema(), DEFAULT_TOP_K);
        if (compiled.isEmpty()) {
            log.info("[CopilotPlanner] site '{}' refine produced nothing grounded — keeping the parsed plan",
                    request.siteName());
            TurDslQueryRequest sorted = applyJudgedSort(body, request, verdict);
            return new TurCopilotQueryPlan(sorted, label, passes,
                    "parse+judge+refine (repair ungrounded, kept parse): " + note(verdict));
        }
        return new TurCopilotQueryPlan(compiled.get(), label, passes,
                "parse+judge+refine: " + note(verdict));
    }

    // ─────────────────────────── passes ───────────────────────────

    /** The T385 parse over the full question; degrades to free-text (no LLM cost). */
    private Parsed parse(PlanRequest request) {
        if (parser.isAvailable() && request.schema() != null && !request.schema().isEmpty()) {
            try {
                return new Parsed(parser.parse(new ParseRequest(request.siteName(), request.locale(),
                        request.query(), request.schema())), true);
            } catch (TurNLFacetParseException e) {
                log.warn("[CopilotPlanner] NL→facet parse failed for '{}': {} — falling back to free-text",
                        request.siteName(), e.getMessage());
            }
        }
        return new Parsed(TurCopilotQueryBodies.freeText(request.query(), DEFAULT_TOP_K), false);
    }

    /**
     * Injects the sort the judge named, when it grounds to a declared numeric field and
     * the plan has no sort yet. This is the cheap half of the refine: the exact failure
     * the block exists to repair ("the parse missed the sort") is fixed without a
     * second generation call, and it is verified against the schema, not trusted.
     */
    private TurDslQueryRequest applyJudgedSort(TurDslQueryRequest body, PlanRequest request,
            TurCopilotPlanVerdict verdict) {
        TurCopilotPlanRepair sortOnly = new TurCopilotPlanRepair(null, null, null,
                verdict.sortField(), verdict.sortOrder(), null);
        // Reuse the compiler purely as the grounding check for (field, order): it only
        // returns a body when the field is declared AND numeric AND the order is valid.
        Optional<TurDslQueryRequest> grounded = compiler.compile(sortOnly, request.schema(), DEFAULT_TOP_K);
        if (grounded.isEmpty() || grounded.get().sort() == null || grounded.get().sort().isEmpty()) {
            return body;
        }
        return TurCopilotQueryBodies.withSort(body, verdict.sortField(),
                verdict.sortOrder().trim().toLowerCase(java.util.Locale.ROOT), DEFAULT_TOP_K);
    }

    /** True when the plan carries no retrieval signal at all — used by the HYBRID gate. */
    boolean isDegenerate(TurDslQueryRequest body) {
        return validator.isDegenerate(body);
    }

    /** Clamp the configured depth into the supported {@code [0, 2]} range. */
    private static int clampDepth(int maxPasses) {
        return Math.clamp(maxPasses, 0, MAX_SUPPORTED_PASSES);
    }

    private static String note(TurCopilotPlanVerdict verdict) {
        StringBuilder sb = new StringBuilder();
        if (verdict.missingFilters() != null && !verdict.missingFilters().isEmpty()) {
            sb.append("missing=").append(verdict.missingFilters());
        }
        if (verdict.sortField() != null && !verdict.sortField().isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append("sort=").append(verdict.sortField())
                    .append(' ').append(verdict.sortOrder());
        }
        if (verdict.ungroundedField() != null && !verdict.ungroundedField().isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append("ungrounded=").append(verdict.ungroundedField());
        }
        return sb.isEmpty() ? "no findings" : sb.toString();
    }

    /** The initial parse: the body plus whether it cost an LLM call. */
    private record Parsed(TurDslQueryRequest body, boolean llmCall) {
    }
}
