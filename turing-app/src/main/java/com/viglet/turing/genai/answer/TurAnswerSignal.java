/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

/**
 * T845 / §LXII.14 — the answer-level interaction signals the answer surface emits
 * after an answer is produced, fed to the {@code selftuning} loop.
 *
 * <p>Each signal is flagged {@link #isRelevanceMiss() relevance-miss} or not. A
 * relevance-miss is explicit evidence the answer/retrieval fell short — the
 * self-tuning miner treats those as failures the same way it treats an
 * {@code ABANDONED} session. The block's flagship signal is
 * {@link #FACET_REFINED_AFTER_ANSWER}: on a real faceted index, a user narrowing a
 * facet <em>after</em> reading an answer is unusually clean feedback that retrieval
 * returned the wrong subset. {@link #THUMBS_DOWN} is unambiguous dissatisfaction.
 * The rest ({@link #CITATION_CLICKED}, {@link #FOLLOW_UP_TAKEN},
 * {@link #RE_ANSWER_REQUESTED}, {@link #THUMBS_UP}) are engagement, not misses, so
 * they never pollute the failure digest.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurAnswerSignal {

    /** A source card or inline {@code [n]} citation was clicked — engagement. */
    CITATION_CLICKED(false),
    /** A suggested follow-up question was taken — engagement. */
    FOLLOW_UP_TAKEN(false),
    /** The answer was re-run (e.g. after a freshness/drift prompt) — engagement. */
    RE_ANSWER_REQUESTED(false),
    /**
     * A facet was refined <em>after</em> the answer was read — the block's clean
     * relevance-miss signal (retrieval answered over the wrong candidate subset).
     */
    FACET_REFINED_AFTER_ANSWER(true),
    /** The answer was rated up — engagement. */
    THUMBS_UP(false),
    /** The answer was rated down — an explicit relevance/quality miss. */
    THUMBS_DOWN(true);

    private final boolean relevanceMiss;

    TurAnswerSignal(boolean relevanceMiss) {
        this.relevanceMiss = relevanceMiss;
    }

    /**
     * Whether this signal is evidence the answer missed — the discriminator the
     * self-tuning miner keys on to fold answer feedback into an agent's failure
     * digest.
     */
    public boolean isRelevanceMiss() {
        return relevanceMiss;
    }

    /**
     * Lenient parse of a wire value to a signal, or {@code null} when it is
     * null/blank/unknown — so a malformed client payload is dropped fail-open
     * rather than 500-ing the endpoint.
     */
    public static TurAnswerSignal fromWire(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TurAnswerSignal.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
