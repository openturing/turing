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
import java.time.LocalDateTime;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T48 — a named, configurable async job invoked from a chat-flow
 * {@code scheduleAgent} node. The node enqueues an execution to JMS so the
 * chat turn returns immediately; a worker (see {@code TurRoutineQueue})
 * runs the routine and writes its result into a slot via
 * {@code POST /chat/slots}, which the auto-resume listener picks up to
 * advance the parked flow.
 *
 * <p>For MVP the routine body is a {@link TurRoutineKind#NATIVE} reference
 * to a Spring AI {@code @Tool} method (the same machinery
 * {@code functionCall} uses synchronously). The async wrapper exists so
 * long-running side effects (proposal generation, document signing,
 * external API calls with multi-second latency) can run without holding
 * the chat HTTP connection open.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tur_routine")
public class TurRoutine implements Serializable {
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

    /** Stable, admin-visible name. Unique across the system. */
    @Column(name = "name", nullable = false, length = 128, unique = true)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private TurRoutineKind kind = TurRoutineKind.NATIVE;

    /**
     * Name of the {@code @Tool} method to invoke when {@link #kind} is
     * {@link TurRoutineKind#NATIVE}. Looked up through
     * {@code TurNativeToolService.getToolCallbacks(Set)} — same resolution
     * path as the synchronous {@code functionCall} node.
     */
    @Column(name = "nativeToolName", length = 128)
    private String nativeToolName;

    /**
     * Per-routine timeout fallback (ms). The {@code scheduleAgent} node
     * may override; if neither sets it the engine treats anything past
     * this cap as expired and routes via the timeout edge on the next
     * walk-through.
     */
    @Column(name = "defaultTimeoutMs", nullable = false)
    private int defaultTimeoutMs = 60_000;

    /**
     * Groovy script body executed when {@link #kind} is
     * {@link TurRoutineKind#GROOVY}. The script receives the conversation
     * slot map (read-only, as {@code slots}), the routine's input payload
     * (as {@code args}), and a {@code conversationId} binding. The last
     * expression is captured and written to the {@code scheduleAgent}
     * node's {@code outputVariable}. Ignored for {@code NATIVE} routines.
     */
    @Column(name = "groovyScript", columnDefinition = "longtext")
    private String groovyScript;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
