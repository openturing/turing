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
 * Per-conversation runtime state of a {@link TurChatFlow}. The Phase B engine
 * reads it on every chat turn to know which node the user is currently on,
 * and updates {@link #currentNodeId} after the assistant replies.
 *
 * <p>{@link #variablesJson} accumulates values collected across the
 * conversation (e.g. {@code outputVariable} of past nodes). Future steps may
 * use those to evaluate {@code condition} nodes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Getter
@Setter
@Entity
@Table(name = "chat_flow_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chat_flow_state_conversation_flow",
                columnNames = { "conversationId", "flow_id" }))
public class TurChatFlowState implements Serializable {
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

    /**
     * Opaque identifier supplied by the client (the IndexedDB ChatSession id
     * in the front-end). Combined with {@code flow_id} it uniquely identifies
     * a runtime conversation.
     */
    @Column(name = "conversationId", nullable = false, length = 100)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private TurChatFlow flow;

    @Column(name = "currentNodeId", nullable = false, length = 100)
    private String currentNodeId;

    /**
     * Id of the parent state row when this state is running a sub-flow
     * (Sub Flow node in the parent's graph). {@code null} for the root state.
     * The engine uses this to walk back up the call stack — when this state
     * reaches its end node, the parent is advanced past its Sub Flow node.
     *
     * @since 2026.2.6
     */
    @Column(name = "parentStateId", length = 36)
    private String parentStateId;

    @Lob
    @Column(name = "variablesJson", columnDefinition = "longtext")
    private String variablesJson;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
