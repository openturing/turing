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
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One synthetic-user interview transcript within a {@link TurResearchStudy}
 * (Block AW / §XLVI.2). Each persona in the study's roster produces exactly one
 * interview (unique on {@code study_id, persona_id}); the runner (T721) fills its
 * {@code transcriptJson} — a JSON array of {@code {index, question, answer}} turns
 * produced by the interview engine (T720) — and stamps a {@code contentHash} of
 * the study's interview inputs so an unchanged re-run reuses the transcript
 * instead of re-interviewing.
 *
 * <p>The study side is a real FK (removing a study cascades its interviews away);
 * {@code personaId} is a soft reference to {@code ai_persona.id}. The raw
 * transcript is not serialized directly to the client — the service maps it into
 * typed turn DTOs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_research_interview",
        uniqueConstraints = @UniqueConstraint(name = "uk_research_interview",
                columnNames = {"study_id", "persona_id"}),
        indexes = @Index(name = "idx_research_interview_study", columnList = "study_id"))
public class TurResearchInterview implements Serializable {
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

    /** Soft reference to {@code ai_persona.id} — the interviewed persona. */
    @Column(name = "persona_id", nullable = false, length = 40)
    private String personaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TurResearchInterviewStatus status = TurResearchInterviewStatus.PENDING;

    /** JSON array of {@code {index, question, answer}} turns. */
    @JsonIgnore
    @Lob
    @Column(name = "transcript_json")
    private String transcriptJson;

    /** Convenience count of captured turns (mirrors {@code transcriptJson}). */
    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    /** Hash of the study's interview inputs; drives the re-run skip optimisation. */
    @Column(name = "content_hash", length = 40)
    private String contentHash;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
