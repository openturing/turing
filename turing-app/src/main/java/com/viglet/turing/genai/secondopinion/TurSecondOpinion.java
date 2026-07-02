/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.secondopinion;

/**
 * T522 / §XXVIII.18 — the verdict of a multi-provider "second opinion" check: a
 * cheap model from a <em>different</em> vendor critiqued the primary RAG answer.
 * Surfaced beside the answer as a cross-vendor confidence signal (it does not
 * alter the answer).
 *
 * @param agree         {@code true} when the critic judged the answer accurate +
 *                      supported by the retrieved context
 * @param confidence    optional calibrated confidence in {@code [0,1]} (e.g. from
 *                      OpenAI {@code top_logprobs} when the critic is OpenAI), or
 *                      {@code null} when only a binary verdict is available
 * @param rationale     the critic's one-line reason (for the hover/tooltip)
 * @param criticModel   the critic model name (for the UI/telemetry)
 * @param criticVendor  the critic's vendor/plugin type (always != the answerer's)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSecondOpinion(
        boolean agree,
        Double confidence,
        String rationale,
        String criticModel,
        String criticVendor) {
}
