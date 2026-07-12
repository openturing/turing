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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceStatus;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * One project-scoped content added to a {@link TurPersonaMatchProject}
 * (Block AT / §XLIII). Mirrors Block AA's {@code TurPersonaSource} but is owned
 * by a Match project instead of a persona, so a content is added <em>once per
 * project</em> and evaluated against every persona in the project. Reuses the
 * same {@link TurPersonaSourceType}/{@link TurPersonaSourceStatus} and the same
 * extraction paths (SSRF-guarded URL fetch / indexed-doc resolver / Tika asset
 * parse).
 *
 * <p>{@code contentHash} is stamped from the extracted text so the N×N runner
 * can skip cells whose content is unchanged across scheduled re-runs. The raw
 * {@code cachedText} is never serialized to the client — only a
 * {@link #getCachedTextPreview() preview} and its {@link #getCachedTextLength()
 * length}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_match_source")
public class TurPersonaMatchSource implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int PREVIEW_CHARS = 280;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnore
    private TurPersonaMatchProject project;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TurPersonaSourceType type;

    /** Human-friendly label shown in the studio. */
    @Column(name = "source_name", length = 200)
    private String sourceName;

    /** SN document id (SN_DOC), original filename (ASSET), or absolute URL (URL). */
    @Column(name = "ref", length = 1000)
    private String ref;

    /** Only for SN_DOC — the Semantic Navigation site the document lives in. */
    @Column(name = "site_name", length = 100)
    private String siteName;

    @JsonIgnore
    @Lob
    @Column(name = "cached_text")
    private String cachedText;

    /** Hash of the extracted text; drives the re-run cell-skip optimisation. */
    @Column(name = "content_hash", length = 40)
    private String contentHash;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 16)
    private TurPersonaSourceStatus extractionStatus = TurPersonaSourceStatus.PENDING;

    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    @Transient
    public int getCachedTextLength() {
        return cachedText == null ? 0 : cachedText.length();
    }

    @Transient
    public String getCachedTextPreview() {
        if (cachedText == null) {
            return null;
        }
        String trimmed = cachedText.strip();
        return trimmed.length() <= PREVIEW_CHARS
                ? trimmed
                : trimmed.substring(0, PREVIEW_CHARS) + "…";
    }
}
