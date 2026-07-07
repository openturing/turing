/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.readability;

import java.util.List;

/**
 * Deterministic readability verdict for a piece of content against a reader
 * (Block AA / §XXVI.3). The {@code fitScore} (0–100) is the hard numeric signal
 * the content-fit evaluator (T467) fuses with the LLM's grounded judgment;
 * {@code notes} are short, human-readable explanations of the score.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurReadabilityResult(
        double fitScore,
        TurReadabilityMetrics metrics,
        List<String> notes) {
}
