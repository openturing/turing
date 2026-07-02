/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.chatanalytics;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.service.chatanalytics.TurAnalyticsIntentEvictionListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T28 / §III.5 — per-agent intent catalog entry for the post-hoc chat
 * analytics classifier. Each row carries one human-readable
 * {@link #label} (e.g. {@code "Refund request"}, {@code "Career growth
 * questions"}) plus a newline-separated bag of {@link #samples} —
 * representative user utterances the {@code TurChatIntentClassifier}
 * strategies score session transcripts against.
 *
 * <p>The catalog is the per-agent <em>intent vocabulary</em> the
 * non-LLM classifiers ({@code TurLuceneIntentClassifier} via embedded
 * {@code MoreLikeThis}; the deferred {@code TurEsMltIntentClassifier}
 * via Elasticsearch {@code more_like_this}) work against. Independent
 * from {@link com.viglet.turing.persistence.model.agent.TurChatFlow}'s
 * {@code triggerDescription}: chat flows describe <em>routing</em>
 * intents (where to send a turn); analytics intents describe
 * <em>categorisation</em> intents (what the conversation was about).
 * Same agent can have both, with different vocabularies.
 *
 * <p>Samples are stored as a single newline-separated {@link Lob} for
 * write simplicity — the cost of a junction table buys nothing for the
 * MLT scoring pipeline, which concatenates samples anyway before
 * indexing. Empty samples allowed (admins can stage labels before
 * filling them in); the classifier skips entries with blank samples.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "analytics_intent",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_analytics_intent_agent_label",
                columnNames = { "agent_id", "label" }))
@EntityListeners(TurAnalyticsIntentEvictionListener.class)
public class TurAnalyticsIntent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    /**
     * Human-readable intent label rendered on dashboards (e.g.
     * {@code "Refund request"}, {@code "Plano de carreira"}). Unique per
     * agent — same wording across agents is fine, the unique constraint
     * is {@code (agent_id, label)}.
     */
    @Column(name = "label", nullable = false, length = 128)
    private String label;

    /**
     * Newline-separated bag of representative user utterances the
     * classifier matches conversation transcripts against. {@code @Lob}
     * because real-world catalogs ship dozens of samples per intent and
     * VARCHAR caps lead to silent truncation. Empty/blank is allowed —
     * the classifier just skips the entry.
     */
    @Lob
    @Column(name = "samples", columnDefinition = "longtext")
    private String samples;

    /**
     * Disables the row without deleting it — useful when staging a new
     * intent or sunsetting an old one without losing history. The
     * classifier filters {@code enabled == 1} on every cycle.
     */
    @Column(name = "enabled", nullable = false)
    private int enabled = 1;

    /**
     * Optional admin notes / description (why this intent exists, what
     * it captures). Not consumed by the classifier; rendered on the
     * admin UI for context.
     */
    @Lob
    @Column(name = "description", columnDefinition = "longtext")
    private String description;

    /**
     * Owning AI agent — the catalog is agent-scoped because intent
     * vocabularies differ per product/persona. {@code JsonIgnore} keeps
     * the payload tight (the URL already encodes the agent id).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonIgnore
    private TurAIAgent turAIAgent;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
