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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One cell of a project's N×N fit matrix (Block AT / §XLIII): the persisted
 * result of evaluating a single {@link TurPersonaMatchSource content} against a
 * single persona through Block AA's {@code TurPersonaContentFitEvaluator}. Cells
 * persist across runs and are keyed by {@code (projectId, sourceId, personaId)};
 * a scheduled re-run only recomputes cells whose {@code contentHash} changed.
 *
 * <p>{@code fits}/{@code misfits} hold the evaluator's own grounded output
 * serialized as JSON text (no new judging logic lives here). {@code sourceId}
 * and {@code personaId} are plain columns rather than associations because the
 * matrix is written and read in bulk, not navigated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_match_cell",
        uniqueConstraints = @UniqueConstraint(name = "uk_persona_match_cell",
                columnNames = {"project_id", "source_id", "persona_id"}),
        indexes = @Index(name = "idx_persona_match_cell_project", columnList = "project_id"))
public class TurPersonaMatchCell implements Serializable {
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

    /** Fused fit score (0–100): readability sub-score ⊕ grounded LLM judgment. */
    @Column(name = "fit_score", nullable = false)
    private double fitScore;

    /** Deterministic readability sub-score (0–100). */
    @Column(name = "readability_score", nullable = false)
    private double readabilityScore;

    @Column(name = "summary", length = 1000)
    private String summary;

    /** JSON array of "what suits this reader" bullets. */
    @Lob
    @Column(name = "fits_json")
    private String fitsJson;

    /** JSON array of grounded misfits (span/reason/suggestion). */
    @Lob
    @Column(name = "misfits_json")
    private String misfitsJson;

    /** Whether the grounded LLM judge contributed (false = readability-only). */
    @Column(name = "llm_used", nullable = false)
    private boolean llmUsed;

    /** The source content hash this cell was computed from (drives re-run skip). */
    @Column(name = "content_hash", length = 40)
    private String contentHash;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;
}
