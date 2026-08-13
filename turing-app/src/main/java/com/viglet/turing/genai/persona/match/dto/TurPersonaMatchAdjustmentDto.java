/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match.dto;

import java.time.Instant;
import java.util.List;

/**
 * Suggested adjustments that close the bidirectional tuning loop (Block AT /
 * §XLIII, T704): concrete edits to make either to a <em>content</em> (so it
 * serves a persona better) or to a <em>persona</em> (so it describes the audience
 * the content actually reaches). Advisory only — nothing here is ever applied
 * automatically; an operator reads the actions and decides.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchAdjustmentDto(
        String projectId,
        /** Whether the grounded LLM produced these, or the deterministic fallback did. */
        boolean llmUsed,
        /** Why the LLM was not used, when it was not. */
        String error,
        Instant generatedAt,
        List<Item> items) {

    /** Which side of the loop an item adjusts. */
    public enum Target {
        CONTENT, PERSONA
    }

    /**
     * One adjustment.
     *
     * @param avgFit     the target's current average fit across the matrix
     * @param delta      how that average moved since the previous run ({@code null}
     *                   when there is no previous run to compare against)
     * @param actions    the concrete edits to consider, most important first
     * @param fieldEdits T831 — for a PERSONA item, the audience fields the
     *                   suggestion would change, each already grounded against the
     *                   persona's current value. Empty whenever there is nothing an
     *                   operator could confirm, which is what the studio reads to
     *                   decide whether an Apply affordance exists at all; always
     *                   empty on a CONTENT item, whose target Turing does not own.
     */
    public record Item(Target target, String targetId, String targetName, double avgFit,
            Double delta, String rationale, List<String> actions, List<FieldEdit> fieldEdits) {

        /** The pre-T831 shape, for callers that carry no field edits. */
        public Item(Target target, String targetId, String targetName, double avgFit,
                Double delta, String rationale, List<String> actions) {
            this(target, targetId, targetName, avgFit, delta, rationale, actions, List.of());
        }
    }

    /**
     * One proposed audience-field change, as a diff the operator confirms field by
     * field (T831 / §LXI.7). Never a bulk accept and never automatic: an LLM
     * rewriting an audience definition unattended is the failure mode the
     * advisory-only rule exists to prevent.
     *
     * @param field        the audience field, as a {@code TurPersonaAudienceField}
     *                     constant name
     * @param currentValue what the persona says today, {@code null} when unset —
     *                     the left-hand side of the diff
     * @param proposedValue the suggested replacement, never equal to
     *                     {@code currentValue} (a no-op edit is dropped)
     * @param reason       one sentence on why the current value is wrong
     * @param allowedValues the closed value set for an enumerated field, empty for
     *                     free text — so the studio can render a picker without
     *                     knowing the enums
     */
    public record FieldEdit(String field, String currentValue, String proposedValue,
            String reason, List<String> allowedValues) {
    }
}
