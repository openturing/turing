/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;

/**
 * Append-only audit-log repository for chat-flow slot writes (T60).
 *
 * <p>No {@code @Cacheable}: the audit row is written on every slot mutation
 * and read primarily through the admin timeline view — the read pattern is
 * not hot enough to justify cache plumbing and the eviction surface would be
 * onerous (every write would need to invalidate the per-conversation list).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurChatSlotAuditRepository extends JpaRepository<TurChatSlotAuditEntry, String> {

    /**
     * Full audit trail for a conversation, oldest first — renders as a
     * chronological timeline in the admin Session Info panel.
     */
    List<TurChatSlotAuditEntry> findByConversationIdOrderByTsAsc(String conversationId);

    /**
     * Drops all rows attached to the given conversation. Called by retention
     * jobs and by GDPR/LGPD "forget visitor" flows so audit trails of
     * deleted conversations don't outlive the conversation itself.
     */
    @Modifying
    @Query("delete from TurChatSlotAuditEntry e where e.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
