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

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurChatCitationRecord;

/**
 * §X.7.d / T155 — persisted Anthropic Citations + their drift verdicts.
 *
 * <p>No {@code @Cacheable}: rows are written on every cited turn and the scan
 * job updates verdict columns in place, so the eviction surface would be
 * onerous for no read-hotness gain (reads are the operator drift panel + the
 * periodic scan, neither hot). Mirrors {@link TurChatSlotAuditRepository}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurChatCitationRecordRepository extends JpaRepository<TurChatCitationRecord, String> {

    /** Every citation captured for a conversation, oldest answer first. */
    List<TurChatCitationRecord> findByConversationIdOrderByAnswerProducedAtAsc(String conversationId);

    /** Stale-only view for a conversation (the compliance "what drifted?" answer). */
    List<TurChatCitationRecord> findByConversationIdAndCitationStaleTrueOrderByAnswerProducedAtAsc(
            String conversationId);

    /**
     * Records the drift scan should re-resolve this run: still fresh, answered
     * inside the check window, and either never checked or checked before the
     * re-check cutoff. Newest answers first so the most relevant citations are
     * verified before the {@code maxPerRun} budget runs out.
     */
    @Query("""
            select r from TurChatCitationRecord r
            where r.citationStale = false
              and r.answerProducedAt >= :windowStart
              and (r.lastCheckedAt is null or r.lastCheckedAt < :recheckBefore)
            order by r.answerProducedAt desc
            """)
    List<TurChatCitationRecord> findDueForScan(@Param("windowStart") Instant windowStart,
            @Param("recheckBefore") Instant recheckBefore, Pageable pageable);

    long countByConversationIdAndCitationStaleTrue(String conversationId);

    /**
     * Drops all citation records for a conversation — called by the LGPD/GDPR
     * "forget visitor" path so provenance audit trails don't outlive the
     * conversation they describe.
     */
    @Modifying
    @Query("delete from TurChatCitationRecord r where r.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
