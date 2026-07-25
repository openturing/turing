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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetEvalReport.CaseResult;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.ParseRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs a {@link TurNLFacetEvalPack} (T385 / §XX.5): for each case it asks the
 * {@link TurNLFacetParser} to turn the prose into a structured query, scores it
 * with {@link TurNLFacetEvalScorer}, and aggregates a {@link TurNLFacetEvalReport}.
 *
 * <p>The declared field schema comes from the pack when present (self-contained
 * fixtures), otherwise it is resolved from the live SN site named by the pack —
 * the same field source {@code dsl_get_mappings} exposes to the LLM, so the eval
 * runs against exactly the schema the parser sees in production.
 *
 * <p>Like the agent eval runner this is intentionally not {@code @Transactional}:
 * a run makes one real LLM call per case and can take seconds.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNLFacetEvalService {

    private final TurNLFacetParser parser;
    private final TurNLFacetEvalScorer scorer;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurNLFacetEvalService(TurNLFacetParser parser,
            TurNLFacetEvalScorer scorer,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.parser = parser;
        this.scorer = scorer;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    /** True when the underlying parser can run (a usable default LLM exists). */
    public boolean isAvailable() {
        return parser.isAvailable();
    }

    /**
     * Run every case of {@code pack} and aggregate the report. Cases that fail to
     * parse are scored as failures (with the parse error) rather than aborting the
     * whole run.
     */
    public TurNLFacetEvalReport run(TurNLFacetEvalPack pack) {
        if (pack == null) {
            return TurNLFacetEvalReport.error("(null)", "No eval pack provided");
        }
        if (pack.cases().isEmpty()) {
            return TurNLFacetEvalReport.error(pack.name(), "Eval pack has no cases");
        }
        if (!parser.isAvailable()) {
            return TurNLFacetEvalReport.error(pack.name(),
                    "No usable default LLM configured for NL→facet parsing");
        }

        List<TurNLFacetField> schema = resolveSchema(pack);
        if (schema.isEmpty()) {
            return TurNLFacetEvalReport.error(pack.name(),
                    "No field schema (pack declares none and site '%s' has no fields)"
                            .formatted(pack.index()));
        }

        List<CaseResult> results = new ArrayList<>();
        for (TurNLFacetEvalCase evalCase : pack.cases()) {
            results.add(runCase(pack, evalCase, schema));
        }
        return aggregate(pack.name(), results);
    }

    // ─────────────────────────── Per-case ───────────────────────────

    private CaseResult runCase(TurNLFacetEvalPack pack, TurNLFacetEvalCase evalCase,
            List<TurNLFacetField> schema) {
        try {
            TurDslQueryRequest actual = parser.parse(
                    new ParseRequest(pack.index(), pack.locale(), evalCase.query(), schema));
            return scorer.score(evalCase.name(), evalCase.expect(), actual, schema);
        } catch (TurNLFacetParseException e) {
            log.warn("[NLFacetEval] case '{}' parse failed: {}", evalCase.name(), e.getMessage());
            return new CaseResult(evalCase.name(), false, 0d,
                    List.of("parse failed: " + e.getMessage()), List.of(), e.getMessage());
        }
    }

    private TurNLFacetEvalReport aggregate(String packName, List<CaseResult> results) {
        int caseCount = results.size();
        int passedCount = (int) results.stream().filter(CaseResult::passed).count();
        boolean passed = caseCount > 0 && passedCount == caseCount;
        double score = results.isEmpty() ? 0d
                : results.stream().mapToDouble(CaseResult::score).average().orElse(0d);
        return new TurNLFacetEvalReport(packName, passed, caseCount, passedCount, score, results, null);
    }

    // ─────────────────────────── Schema resolution ───────────────────────────

    /**
     * The declared field schema a pack's cases are parsed against: the pack's own
     * {@code fields} when it is self-contained, otherwise the live SN site named by
     * {@code index}. Empty when neither yields anything.
     *
     * <p>Public since T821 so the planning-strategy comparison
     * ({@code TurCopilotPlanningComparisonService}) resolves the schema exactly the
     * way a plain eval run does, instead of duplicating the rule.
     */
    public List<TurNLFacetField> resolveSchema(TurNLFacetEvalPack pack) {
        if (!pack.fields().isEmpty()) {
            return pack.fields();
        }
        if (!StringUtils.hasText(pack.index())) {
            return List.of();
        }
        return turSNSiteRepository.findByNameIgnoreCase(pack.index())
                .map(site -> turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1).stream()
                        .map(this::toField)
                        .toList())
                .orElse(List.of());
    }

    private TurNLFacetField toField(TurSNSiteFieldExt ext) {
        TurSEFieldType type = ext.getType() != null ? ext.getType() : TurSEFieldType.TEXT;
        return new TurNLFacetField(ext.getName(), type, ext.getFacet() == 1, ext.getDescription());
    }
}
