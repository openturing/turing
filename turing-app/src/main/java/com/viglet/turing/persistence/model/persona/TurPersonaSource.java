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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One evaluation source attached to a {@link TurPersona} — the "notebook" the
 * content-fit evaluator (Block AA / §XXVI.2) judges against. A source points at
 * an indexed SN document, an uploaded asset, or a remote URL; its extracted
 * text is cached on the row so repeated evaluations and the deterministic
 * readability scorer don't re-fetch.
 *
 * <p>The raw {@code cachedText} is never serialized to the client (it can be a
 * whole PDF); the admin sees only a {@link #getCachedTextPreview() preview} and
 * its {@link #getCachedTextLength() length}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona_source")
public class TurPersonaSource implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int PREVIEW_CHARS = 280;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator. Carried on the child too
     *  (it is not inherited via the FK). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id")
    @JsonIgnore
    private TurPersona turPersona;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TurPersonaSourceType type;

    /** Human-friendly label shown in the admin list. */
    @Column(name = "source_name", length = 200)
    private String sourceName;

    /** The reference: SN document id (SN_DOC), original filename (ASSET), or
     *  the absolute URL (URL). */
    @Column(name = "ref", length = 1000)
    private String ref;

    /** Only for SN_DOC — the Semantic Navigation site the document lives in. */
    @Column(name = "site_name", length = 100)
    private String siteName;

    @JsonIgnore
    @Lob
    @Column(name = "cached_text")
    private String cachedText;

    @Column(name = "extracted_at")
    private Instant extractedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 16)
    private TurPersonaSourceStatus extractionStatus = TurPersonaSourceStatus.PENDING;

    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    /** Length of the cached text — surfaced to the admin instead of the body. */
    @Transient
    public int getCachedTextLength() {
        return cachedText == null ? 0 : cachedText.length();
    }

    /** First {@value #PREVIEW_CHARS} characters of the cached text. */
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
