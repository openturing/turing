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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Immutable record of a finished chat-flow run. The engine writes one row
 * the moment the runtime state transitions to a terminal node (real end
 * node or the synthetic {@code __abandoned__} marker), so each submission
 * is preserved even when the same conversation runs the flow again later
 * (e.g. {@code triggerMode = ALWAYS}).
 *
 * <p>Compare to {@link TurChatFlowState}: that entity tracks the
 * <em>in-flight</em> state of a single conversation+flow pair, gets reset
 * on re-trigger, and is unique on {@code (conversationId, flow_id)}. This
 * one is append-only history.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Getter
@Setter
@Entity
@Table(name = "chat_flow_submission",
        indexes = {
                @Index(name = "ix_chat_flow_submission_flow_completed",
                        columnList = "flow_id,completedAt"),
                @Index(name = "ix_chat_flow_submission_conversation",
                        columnList = "conversationId")
        })
public class TurChatFlowSubmission implements Serializable {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private TurChatFlow flow;

    @Column(name = "conversationId", nullable = false, length = 100)
    private String conversationId;

    @Lob
    @Column(name = "variablesJson", columnDefinition = "longtext")
    private String variablesJson;

    @Column(name = "completedAt", nullable = false)
    private LocalDateTime completedAt;

    /** Authenticated principal name when available, null for anonymous chats. */
    @Column(name = "userId", length = 100)
    private String userId;

    /**
     * The node id at which the run terminated. Either the id of a real
     * {@code end} node in the graph, or the literal {@code "__abandoned__"}
     * when the user quit a flow that has no end node.
     */
    @Column(name = "endNodeId", nullable = false, length = 100)
    private String endNodeId;
}
