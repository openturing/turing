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
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T600 / §XXXIII.15 — a named, reusable, <b>agent-decoupled</b> grader stack
 * (e.g. "Lead-capture QA"): an ordered set of {@link TurEvalGraderConfig}s with
 * their weights / thresholds / blocking policy, shareable across agents. An
 * eval set binds one via {@code TurAgentEvalSet.graderStackId}; the runner then
 * uses the stack's configs instead of the set's own.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "tur_eval_grader_stack")
public class TurEvalGraderStack implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 — multi-tenancy discriminator. Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * The stack's grader entries — same aggregate lifecycle as {@code
     * TurAgentEvalSet.graderConfigs}: EAGER + cascade so it travels and is
     * available to the non-transactional runner.
     */
    @OneToMany(mappedBy = "turEvalGraderStack", orphanRemoval = true,
            fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("sortOrder ASC")
    @JsonIgnore
    private Set<TurEvalGraderConfig> configs = new LinkedHashSet<>();
}
