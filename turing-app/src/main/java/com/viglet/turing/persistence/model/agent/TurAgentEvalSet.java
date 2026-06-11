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
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T285 / §XV.1 — a per-agent <b>golden set</b> of regression {@link
 * TurAgentEvalCase}s. The Agent-CI gate replays each case through the real
 * chat-flow engine and scores the result, so a non-engineer who edits a
 * prompt or a flow can prove they didn't regress <em>before</em> publishing.
 *
 * <p>Owned by exactly one {@link TurAIAgent}; {@code name} is unique within
 * that scope. Exported / imported with the agent (cases inline). When {@code
 * blocking} is set, a red gate hard-blocks the flow editor's publish; when
 * clear, it only warns — same affordance authors know from the T94 lint
 * panel.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tur_agent_eval_set",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_eval_set_agent_name",
                columnNames = { "agent_id", "name" }))
public class TurAgentEvalSet implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /** 1 = the set runs in the gate; 0 = parked (authoring in progress). */
    @Column(nullable = false)
    private int enabled = 1;

    /**
     * When 1, a red gate result hard-blocks the flow editor's publish; when
     * 0, the editor only surfaces a warning. Default 0 (warn) so adding a
     * golden set never breaks an author's existing publish flow until they
     * opt in. Mirrors the {@code behaviorChanges = opt-in flag} convention.
     */
    @Column(name = "blocking", nullable = false)
    private int blocking = 0;

    /**
     * Cases of this set, eagerly loaded and cascade-persisted as one
     * aggregate. reason: a golden set is always authored, exported, and
     * scored together with its (small, bounded) case list — same pattern as
     * {@code TurIntent.actions}; never accessed without the parent.
     */
    @OneToMany(mappedBy = "turAgentEvalSet", orphanRemoval = true,
            fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("sortOrder ASC")
    private Set<TurAgentEvalCase> cases = new LinkedHashSet<>();

    /**
     * Owning AI agent. Hidden from JSON because the client always knows the
     * agent id from the URL and we don't want to serialise the agent graph
     * on every list response.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    @JsonIgnore
    private TurAIAgent turAIAgent;
}
