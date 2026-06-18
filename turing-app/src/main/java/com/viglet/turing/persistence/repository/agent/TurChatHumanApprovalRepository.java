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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;
import com.viglet.turing.persistence.model.agent.TurHumanApprovalStatus;

/**
 * Repository for {@link TurChatHumanApproval} pending-approval records (T119).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurChatHumanApprovalRepository extends JpaRepository<TurChatHumanApproval, String> {

    /** Resolve an approval by the opaque token embedded in its resume URL. */
    Optional<TurChatHumanApproval> findByResumeToken(String resumeToken);

    /**
     * The still-pending approval for a conversation+node, if any. Used by the
     * node executor to fire the notification exactly once (idempotent re-entry).
     */
    Optional<TurChatHumanApproval> findFirstByConversationIdAndNodeIdAndStatus(
            String conversationId, String nodeId, TurHumanApprovalStatus status);

    /** Every pending approval for a conversation (admin force-resolve path). */
    List<TurChatHumanApproval> findByConversationIdAndStatus(
            String conversationId, TurHumanApprovalStatus status);

    /**
     * Pending approvals whose deadline has passed — fed to the timeout sweep.
     * {@code expiresAt = null} rows (wait-indefinitely) are excluded by the
     * {@code <} comparison.
     */
    List<TurChatHumanApproval> findByStatusAndExpiresAtBefore(
            TurHumanApprovalStatus status, LocalDateTime cutoff);

    /** Pending approvals newest-first — powers the admin pending-approvals list. */
    List<TurChatHumanApproval> findByStatusOrderByCreatedAtDesc(TurHumanApprovalStatus status);
}
