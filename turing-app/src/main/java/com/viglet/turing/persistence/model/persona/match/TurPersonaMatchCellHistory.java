/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona.match;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One point in a matrix cell's fit history (Block AT / §XLIII, T704). A
 * {@link TurPersonaMatchCell cell} is overwritten in place by every run, so it
 * only ever knows "now"; this append-only series is what remembers how a
 * {@code (content, persona)} pair's fit <em>moved</em> across runs — the memory
 * drift detection and the trend chart read from.
 *
 * <p>One row is written per cell per run — including cells the content-hash
 * guard reused unchanged — so a run's average is computed over the whole matrix
 * and not just over what happened to be recomputed. The series is trimmed to the
 * most recent {@code turing.persona-match.history.max-points} points per cell,
 * which bounds growth for a project on a DAILY cadence.
 *
 * <p>{@code sourceId}/{@code personaId} are plain columns (not associations) for
 * the same reason as on the cell: the series is written and read in bulk, never
 * navigated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_match_cell_history",
        indexes = {
                @Index(name = "idx_persona_match_hist_project", columnList = "project_id"),
                @Index(name = "idx_persona_match_hist_cell",
                        columnList = "project_id, source_id, persona_id")
        })
public class TurPersonaMatchCellHistory implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "project_id", nullable = false, length = 40)
    private String projectId;

    @Column(name = "source_id", nullable = false, length = 40)
    private String sourceId;

    @Column(name = "persona_id", nullable = false, length = 40)
    private String personaId;

    /** The fused fit score (0–100) as of this run. */
    @Column(name = "fit_score", nullable = false)
    private double fitScore;

    @Column(name = "readability_score", nullable = false)
    private double readabilityScore;

    /** Whether the grounded LLM judge contributed to this point. */
    @Column(name = "llm_used", nullable = false)
    private boolean llmUsed;

    /**
     * Whether the run reused the previous verdict (content hash unchanged) rather
     * than re-evaluating — a flat stretch in the series that cost nothing.
     */
    @Column(name = "reused", nullable = false)
    private boolean reused;

    /** The source content hash this point was computed from. */
    @Column(name = "content_hash", length = 40)
    private String contentHash;

    /** The run instant — shared by every point written in the same run. */
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;
}
