/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

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
 * T603 / §XXXIII.18 — one rolling <b>online-eval snapshot</b>: the quality of a
 * window of an agent's live production traffic, graded in the background. Unlike
 * {@link TurAgentEvalReport} (a pre-publish golden-set run) this describes real,
 * already-produced conversations sampled after publish.
 *
 * <p>Each snapshot pins the window it covered and three composed signal rates —
 * negative sentiment (T87), stale citation (T155), failing sessions (T447) —
 * alongside the grader-stack score/pass-rate (when a stack is bound). The first
 * snapshot per agent becomes the {@code baseline}; later snapshots are compared
 * to it and flagged {@code driftDetected} with a human-readable {@code driftReason}
 * when quality falls off. Agent-scoped by a plain {@code agentId} column (like
 * {@link TurChatCitationRecord}) — reads are the operator drift panel + the
 * sweep, neither hot enough to warrant a JPA association.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_online_eval_snapshot",
        indexes = @Index(name = "idx_online_eval_agent", columnList = "agentId"))
public class TurOnlineEvalSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Owning agent — plain column, not a JPA relation (see class Javadoc). */
    @Column(name = "agentId", length = 36, nullable = false)
    private String agentId;

    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @Column(name = "windowStart", nullable = false)
    private Instant windowStart;

    @Column(name = "windowEnd", nullable = false)
    private Instant windowEnd;

    /** How many recent sessions the window sampled. */
    @Column(name = "sampledSessions", nullable = false)
    private int sampledSessions;

    /** How many sampled sessions had at least one applicable grader (were scored). */
    @Column(name = "gradedSessions", nullable = false)
    private int gradedSessions;

    /**
     * The grader stack (T600) used to score, or {@code null} when no stack was
     * bound (signal-only snapshot).
     */
    @Column(name = "graderStackId", length = 36)
    private String graderStackId;

    /**
     * Mean grader-stack score across graded sessions, in [0,1]; {@code -1} when
     * no grading ran (no stack bound or no applicable grader).
     */
    @Column(name = "meanScore", nullable = false)
    private double meanScore;

    /** Fraction of graded sessions whose aggregate passed; {@code -1} when no grading ran. */
    @Column(name = "passRate", nullable = false)
    private double passRate;

    /** Fraction of sampled sessions with NEGATIVE / FRUSTRATED sentiment (T87). */
    @Column(name = "negativeSentimentRate", nullable = false)
    private double negativeSentimentRate;

    /**
     * Fraction of sampled sessions with at least one stale citation (T155);
     * {@code -1} when citation-drift detection is disabled.
     */
    @Column(name = "staleCitationRate", nullable = false)
    private double staleCitationRate;

    /** Fraction of sampled sessions the T447 miner classes as failing. */
    @Column(name = "failingSessionRate", nullable = false)
    private double failingSessionRate;

    /**
     * True when this snapshot is the agent's healthy baseline — the yardstick
     * later windows are compared against. Exactly zero or one per agent.
     */
    @Column(name = "baseline", nullable = false)
    private boolean baseline;

    /** True when this window drifted versus the baseline. */
    @Column(name = "driftDetected", nullable = false)
    private boolean driftDetected;

    /** Human-readable, semicolon-joined list of the triggered drift reasons. */
    @Column(name = "driftReason", columnDefinition = "longtext")
    private String driftReason;
}
