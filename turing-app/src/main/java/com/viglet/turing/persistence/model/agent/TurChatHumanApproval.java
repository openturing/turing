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

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A pending human-in-the-loop approval raised by a {@code humanApproval}
 * chat-flow node (T119 / §IX.5.a). One row is created the moment the engine
 * walks onto the node: the conversation is parked, a notification is dispatched
 * on the configured channel, and the {@link #resumeToken} embedded in the
 * notification lets an operator resolve it through
 * {@code GET/POST /api/genai/approval/{token}} without a Turing session.
 *
 * <p>The operator's decision is written into the conversation's
 * {@link #approvalSlot}; downstream {@code condition}/{@code switch} nodes then
 * branch on it. When {@link #expiresAt} elapses without a decision, the daily/
 * minutely sweep auto-resolves the record per the node's {@code timeoutBehavior}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "chat_human_approval",
        indexes = {
                @Index(name = "ix_chat_human_approval_token", columnList = "resumeToken", unique = true),
                @Index(name = "ix_chat_human_approval_conv_node", columnList = "conversationId,nodeId"),
                @Index(name = "ix_chat_human_approval_status_expires", columnList = "status,expiresAt")
        })
public class TurChatHumanApproval implements Serializable {
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

    /** Opaque, unguessable token embedded in the notification's resume URL. */
    @Column(name = "resumeToken", nullable = false, length = 64)
    private String resumeToken;

    @Column(name = "conversationId", nullable = false, length = 100)
    private String conversationId;

    /** The {@code humanApproval} node's id within its flow graph. */
    @Column(name = "nodeId", nullable = false, length = 100)
    private String nodeId;

    /** The flow this node belongs to (plain reference — no FK join needed). */
    @Column(name = "flowId", length = 40)
    private String flowId;

    /** Slot the operator's decision is written into (defaults to the node's). */
    @Column(name = "approvalSlot", nullable = false, length = 100)
    private String approvalSlot;

    /** Notification channel used (slack | email | webhook), for audit. */
    @Column(name = "channel", length = 20)
    private String channel;

    /** Channel-specific destination (e-mail / Slack URL / webhook name), for audit. */
    @Column(name = "target", length = 500)
    private String target;

    /** Rendered notification body — also shown on the approval page. */
    @Lob
    @Column(name = "promptText", columnDefinition = "longtext")
    private String promptText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TurHumanApprovalStatus status = TurHumanApprovalStatus.PENDING;

    /** Resolved value written into {@link #approvalSlot} (approve|reject|edit:…). */
    @Column(name = "decision", length = 500)
    private String decision;

    /** {@code auto_reject} (default) | {@code auto_approve} — applied on timeout. */
    @Column(name = "timeoutBehavior", length = 20)
    private String timeoutBehavior;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /** When the pending approval auto-resolves; {@code null} = wait indefinitely. */
    @Column(name = "expiresAt")
    private LocalDateTime expiresAt;

    @Column(name = "decidedAt")
    private LocalDateTime decidedAt;
}
