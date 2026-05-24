/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.rag;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T24b / §III.2 — per-locale BM25 core configured for hybrid RAG retrieval.
 *
 * <p>Each row maps {@code (TurStoreInstance, locale)} to a dedicated core /
 * index in the configured {@link TurSEInstance} (Solr, Elasticsearch, or
 * Lucene-embedded via {@code TurSearchEnginePlugin}). The core holds the
 * SAME chunks as the vector store identified by {@code turStoreInstance}
 * — same UUIDs, same {@code content} text — but tokenized through the
 * locale-appropriate analyzer chain (PortugueseAnalyzer for {@code pt-*},
 * EnglishAnalyzer for {@code en-*}, etc.) and scored via BM25.
 *
 * <h2>Core naming convention</h2>
 *
 * The {@link #coreName} field is auto-generated and immutable for the
 * row's lifetime:
 * <pre>
 *   rag_{storeInstanceShortId}_{localeTag}
 * </pre>
 * where {@code storeInstanceShortId} is the first 8 characters of the
 * {@link TurStoreInstance} id UUID (enough for uniqueness inside
 * a single Turing deployment), and {@code localeTag} is the IETF BCP 47
 * tag (e.g. {@code pt-BR}, {@code en-US}). The {@code rag_} prefix is
 * load-bearing: it lets {@code TurSNSiteSearchAPI} filter these cores
 * out of public search results so RAG content stays private.
 *
 * <h2>Status flow</h2>
 *
 * <pre>
 *   NOT_PROVISIONED → PROVISIONING → PROVISIONED
 *                                  ↘ ERROR (transient — admin can retry)
 *
 *   PROVISIONED → DELETING → (row removed)
 * </pre>
 *
 * The provisioner ({@code TurRagBm25CoreProvisioner}) sets {@link #status}
 * to {@link Status#PROVISIONING} before calling the SE plugin, then to
 * {@link Status#PROVISIONED} on success or {@link Status#ERROR} with a
 * captured message in {@link #lastError} on failure.
 *
 * <p>{@link #docCount} is a denormalized cache updated on every successful
 * indexing batch — the source of truth lives in the SE. Refreshed via a
 * background job (or manually from the admin UI).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Getter
@Setter
@Entity
@Table(name = "rag_bm25_core",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rag_bm25_core_store_locale",
                columnNames = { "store_instance_id", "locale" }))
public class TurRagBm25Core implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * Vector store whose chunks this core mirrors for BM25 search.
     * Together with {@link #locale} forms the natural unique key.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_instance_id", nullable = false)
    private TurStoreInstance turStoreInstance;

    /**
     * Search engine instance hosting this core. Plugin type
     * ({@code solr}/{@code elasticsearch}/{@code lucene}) determines
     * the schema + similarity bootstrap performed by the provisioner.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "se_instance_id", nullable = false)
    private TurSEInstance turSEInstance;

    /**
     * IETF BCP 47 locale tag (e.g. {@code pt-BR}, {@code en-US}). Stored
     * as a {@link Locale} for type safety; serialized via JPA's default
     * locale converter (toLanguageTag/forLanguageTag round-trip).
     */
    @Column(name = "locale", nullable = false, length = 16)
    private Locale locale;

    /**
     * Auto-generated, immutable core/index name. See class Javadoc for
     * the naming convention. Set by the provisioner on row creation —
     * NEVER edited afterwards (renaming would orphan the SE core).
     */
    @Column(name = "core_name", nullable = false, length = 128)
    private String coreName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.NOT_PROVISIONED;

    /**
     * Denormalized count of documents currently indexed in the SE core.
     * Refreshed by the indexer on every successful batch + manually from
     * the admin UI. May lag the true SE count by seconds; treat as a
     * monitoring hint, not a billing surface.
     */
    @Column(name = "doc_count", nullable = false)
    private long docCount = 0L;

    /**
     * Captured error message from the last provisioning attempt that
     * resulted in {@link Status#ERROR}. Cleared on the next successful
     * provisioning. Null when status is anything other than ERROR.
     */
    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Lifecycle state machine for a BM25 core. See class Javadoc for
     * the transition graph.
     */
    public enum Status {
        /** Row created but the SE core hasn't been created yet. Default. */
        NOT_PROVISIONED,
        /** Provisioner is currently calling the SE plugin to create the core. */
        PROVISIONING,
        /** SE core exists and is ready to receive documents / serve queries. */
        PROVISIONED,
        /** Last provisioning attempt failed; see {@link #lastError}. */
        ERROR,
        /** Admin requested deletion; deletion in progress. */
        DELETING
    }
}
