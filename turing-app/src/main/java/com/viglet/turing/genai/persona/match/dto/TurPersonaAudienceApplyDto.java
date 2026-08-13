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
 * The confirm-and-write half of T831 / §LXI.7.
 *
 * <p>The request carries only the fields the operator actually confirmed — never
 * the whole audience facet and never the whole item. That is the difference
 * between an Apply action and an auto-apply: a field the operator left alone is
 * absent from the request, so nothing in the write path can touch it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurPersonaAudienceApplyDto {

    private TurPersonaAudienceApplyDto() {
        // holder
    }

    /**
     * @param personaId the persona to write — must be one the project actually
     *                  scores, so an Apply cannot reach a persona the operator was
     *                  never shown
     * @param rationale the suggestion's own sentence, carried through so the
     *                  provenance row records the cause and not just the change
     * @param llmUsed   whether the suggestion came from the grounded judge or the
     *                  deterministic fallback
     * @param fields    the confirmed field/value pairs, one per field
     */
    public record Request(String personaId, String rationale, boolean llmUsed,
            List<ConfirmedField> fields) {
    }

    /** One field the operator ticked, with the value they saw when they ticked it. */
    public record ConfirmedField(String field, String value) {
    }

    /**
     * @param applied the fields that were written, with the value each replaced
     * @param skipped the confirmed fields that were <em>not</em> written, each with
     *                the reason — an unwritable field name, a value the field does
     *                not accept, or a value already in place. Reported rather than
     *                thrown so one bad field cannot discard the operator's other
     *                confirmations.
     */
    public record Response(String personaId, Instant appliedAt, List<AppliedField> applied,
            List<SkippedField> skipped) {
    }

    public record AppliedField(String field, String oldValue, String newValue) {
    }

    public record SkippedField(String field, String reason) {
    }
}
