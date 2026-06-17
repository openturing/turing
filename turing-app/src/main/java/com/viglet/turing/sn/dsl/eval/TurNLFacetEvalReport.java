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

/**
 * The result of running a {@link TurNLFacetEvalPack} (T385 / §XX.5): an aggregate
 * pass/fail plus the per-case breakdown. Mirrors the shape of the agent eval
 * report (Block&nbsp;K) but is in-memory — the golden expectations live in the
 * pack fixture, so the pack <i>is</i> the baseline and no persisted report is
 * needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurNLFacetEvalReport(
        String packName,
        boolean passed,
        int caseCount,
        int passedCount,
        double score,
        List<CaseResult> results,
        String error) {

    public static TurNLFacetEvalReport error(String packName, String error) {
        return new TurNLFacetEvalReport(packName, false, 0, 0, 0d, List.of(), error);
    }

    /**
     * The score for one case.
     *
     * @param caseName         the case label.
     * @param passed           all expected dimensions matched and no field was
     *                         filtered outside the declared schema.
     * @param score            fraction of expected dimensions matched (0..1).
     * @param findings         human-readable diffs for the dimensions that missed.
     * @param ungroundedFields fields the parser filtered on that are <b>not</b> in
     *                         the declared schema — a hard objective-grounding
     *                         violation.
     * @param error            non-null when the case could not be parsed at all.
     */
    public record CaseResult(
            String caseName,
            boolean passed,
            double score,
            List<String> findings,
            List<String> ungroundedFields,
            String error) {
    }
}
