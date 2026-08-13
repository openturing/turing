/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One confirmed audience-field write, stamped with the Persona Match suggestion
 * it came from (T831 / §LXI.7).
 *
 * <p>A persona is overwritten in place, so it only ever knows "now" — which is
 * what made a suggestion the operator agreed with indistinguishable from a
 * change someone made for an unrelated reason, and the next run's drift
 * unattributable. This append-only series is the record of <em>why</em> an
 * audience definition moved: one row per field the operator confirmed, carrying
 * the value it replaced, the project whose matrix produced the suggestion and
 * the rationale the verdict gave for it.
 *
 * <p>{@code personaId}/{@code projectId} are plain columns (not associations)
 * for the same reason as on {@code TurPersonaMatchCellHistory}: the series is
 * written and read in bulk, never navigated — and a row must outlive the
 * deletion of the project that produced it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_audience_apply_log",
        indexes = {
                @Index(name = "idx_persona_audience_apply_persona", columnList = "persona_id"),
                @Index(name = "idx_persona_audience_apply_project", columnList = "project_id")
        })
public class TurPersonaAudienceApplyLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "persona_id", nullable = false, length = 40)
    private String personaId;

    /**
     * The Persona Match project whose matrix produced the suggestion. Nullable so
     * a row survives the project's deletion rather than blocking it.
     */
    @Column(name = "project_id", length = 40)
    private String projectId;

    /** Which audience field this row wrote. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 32)
    private TurPersonaAudienceField field;

    /** The value the write replaced — {@code null} when the field was unset. */
    @Lob
    @Column(name = "old_value")
    private String oldValue;

    @Lob
    @Column(name = "new_value")
    private String newValue;

    /** The suggestion's own sentence, so the cause survives without the project. */
    @Lob
    @Column(name = "rationale")
    private String rationale;

    /**
     * Whether the suggestion came from the grounded LLM judge or the
     * deterministic fallback — the difference between "a model proposed this" and
     * "the scores alone did".
     */
    @Column(name = "llm_used", nullable = false)
    private boolean llmUsed;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    /** The authenticated principal that confirmed the field. */
    @Column(name = "applied_by", length = 255)
    private String appliedBy;
}
