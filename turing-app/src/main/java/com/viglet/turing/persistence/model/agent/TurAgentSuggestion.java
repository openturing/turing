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

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T447 / §XXIII.6 — one PR-style "suggested change" the self-tuning loop opened
 * for an agent: a drafted revision (currently always the system prompt) that
 * scored BETTER than the current one against the agent's golden sets, awaiting
 * human approval. Never auto-applied.
 *
 * <p>Tenant-scoped via Hibernate's {@code @TenantId}. {@code agentId} is a plain
 * column (not a relation) to keep the suggestion list cheap to query.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "agent_suggestion")
public class TurAgentSuggestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Lifecycle states of a suggestion. */
    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    /** What the suggestion proposes to change. */
    public enum Kind {
        SYSTEM_PROMPT,
        /**
         * T191 / §X.15.e — an overnight-distilled fine-tuned model that beat the
         * baseline on the eval gate. {@code proposedValue} is the candidate model
         * id, {@code currentValue} the incumbent; approval swaps the agent's LLM
         * instance model to the candidate.
         */
        DISTILLATION_CANDIDATE
    }

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(name = "agentId", nullable = false, length = 40)
    private String agentId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private Kind kind = Kind.SYSTEM_PROMPT;

    @Lob
    @Column(name = "currentValue")
    private String currentValue;

    @Lob
    @Column(name = "proposedValue")
    private String proposedValue;

    @Lob
    @Column(name = "rationale")
    private String rationale;

    @Column(name = "baselineScore", nullable = false)
    private double baselineScore;

    @Column(name = "proposedScore", nullable = false)
    private double proposedScore;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "createdAt", nullable = false)
    private long createdAt;

    @Column(name = "decidedAt")
    private long decidedAt;
}
