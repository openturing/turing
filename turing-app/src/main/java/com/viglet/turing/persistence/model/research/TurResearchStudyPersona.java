/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.research;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A study↔persona membership row (Block AW / §XLVI.2, T719) — one participant in
 * a {@link TurResearchStudy}'s ordered audience roster. Like the Persona Match
 * join, {@code personaId} is a <em>soft</em> reference to {@code ai_persona.id}
 * (no hard FK — the persona's name/facets are resolved at read time) while the
 * study side is a real FK so removing a study cascades its roster away. The
 * {@code position} preserves the ordered selection from the shared
 * {@code PersonaSelectGrid}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_research_study_persona")
public class TurResearchStudyPersona implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id")
    @JsonIgnore
    private TurResearchStudy study;

    /** Soft reference to {@code ai_persona.id} — resolved at read time. */
    @Column(name = "persona_id", nullable = false, length = 40)
    private String personaId;

    /** Ordered position within the audience roster (0-based). */
    @Column(name = "position", nullable = false)
    private int position;
}
