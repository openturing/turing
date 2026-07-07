/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * T594 / §XXXIII.9 — the MODEL-vs-human calibration audit: over the reviewed
 * AUDIT tasks (a sample of MODEL-graded cases re-judged by humans), how well the
 * LLM judge agrees with the human consensus, plus a plain-language tuning hint.
 *
 * <p>The 2×2 confusion is model-verdict (rows) vs human-consensus (cols):
 * {@code modelPassHumanPass} agrees, {@code modelPassHumanFail} is an
 * over-pass (the judge is too lenient), {@code modelFailHumanPass} an
 * over-fail (too strict).
 *
 * @param audited            number of reviewed audit items compared
 * @param agreementRate      fraction where model and human agreed, in [0,1]
 * @param kappa              Cohen's kappa (null when undefined / no data)
 * @param interpretation     the strength band
 * @param modelPassHumanPass agree-pass count
 * @param modelPassHumanFail model passed, humans failed (over-pass)
 * @param modelFailHumanPass model failed, humans passed (over-fail)
 * @param modelFailHumanFail agree-fail count
 * @param suggestion         a judge-prompt tuning hint (never auto-applied)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalCalibrationDto(
        int audited,
        Double agreementRate,
        Double kappa,
        String interpretation,
        int modelPassHumanPass,
        int modelPassHumanFail,
        int modelFailHumanPass,
        int modelFailHumanFail,
        String suggestion) {

    /** An empty calibration (no reviewed audit items yet). */
    public static TurEvalCalibrationDto empty() {
        return new TurEvalCalibrationDto(0, null, null, "n/a", 0, 0, 0, 0,
                "Sample a MODEL-graded run and have reviewers audit it to calibrate the judge.");
    }
}
