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

/**
 * Raw, explainable readability metrics produced by {@link TurReadabilityScorer}
 * (Block AA / §XXVI.3). All deterministic — no LLM, no IO.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurReadabilityMetrics(
        double fleschReadingEase,
        double fleschKincaidGrade,
        double avgSentenceLength,
        double avgSyllablesPerWord,
        double complexWordRatio,
        double passiveVoiceRatio,
        int wordCount,
        int sentenceCount) {
}
