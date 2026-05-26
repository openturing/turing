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
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Append-only audit row for a single slot write. Captured by
 * {@code TurChatSlotAuditService} at every code path that mutates a slot:
 * graph node executors ({@link TurChatSlotAuditSource#NODE}), Custom Tool
 * Groovy {@code slots.set(...)} calls ({@link TurChatSlotAuditSource#TOOL}),
 * the public {@code POST /chat/slots} endpoint
 * ({@link TurChatSlotAuditSource#ENDPOINT}), and the document-to-slot LLM
 * extraction service ({@link TurChatSlotAuditSource#EXTRACT}).
 *
 * <p>The log is conversation-scoped, ordered by {@code ts} ascending in the
 * typical query — admins / forensic tooling render it as a timeline next to
 * the chat transcript so a slot value can be traced back to the exact turn
 * (or off-chat write) that produced it. LGPD/GDPR audit + debug forensics.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "chat_slot_write_log",
        indexes = {
                @Index(name = "ix_chat_slot_write_log_conversation_ts",
                        columnList = "conversationId,ts"),
                @Index(name = "ix_chat_slot_write_log_slot",
                        columnList = "slotName,ts")
        })
public class TurChatSlotAuditEntry implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "conversationId", nullable = false, length = 100)
    private String conversationId;

    @Column(name = "slotName", nullable = false, length = 128)
    private String slotName;

    @Lob
    @Column(name = "oldValue", columnDefinition = "longtext")
    private String oldValue;

    @Lob
    @Column(name = "newValue", columnDefinition = "longtext")
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TurChatSlotAuditSource source;

    @Column(name = "originDetail", length = 256)
    private String originDetail;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;
}
