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

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurChatFlowSubmissionRepository extends JpaRepository<TurChatFlowSubmission, String> {

    /** Submissions of a flow ordered newest first — feeds the History page. */
    List<TurChatFlowSubmission> findByFlow_IdOrderByCompletedAtDesc(String flowId);

    /**
     * Every completed (or abandoned) submission tied to a conversation
     * across all flows, newest first. Used by the session-variables API.
     *
     * @since 2026.2.7
     */
    List<TurChatFlowSubmission> findByConversationIdOrderByCompletedAtDesc(String conversationId);

    /**
     * T85 — count of submissions per terminal node id for a given flow.
     * Returns an array of {@code Object[2]} rows where {@code [0]} is the
     * node id and {@code [1]} is the long count. Powers the funnel
     * visualization that shows how many conversations ended at each node
     * (real end nodes + the synthetic {@code __abandoned__} marker).
     *
     * @since 2026.3.1
     */
    @org.springframework.data.jpa.repository.Query(
            "select s.endNodeId, count(s) from TurChatFlowSubmission s "
            + "where s.flow.id = :flowId group by s.endNodeId")
    List<Object[]> countByEndNodeForFlow(@org.springframework.data.repository.query.Param("flowId") String flowId);

    /**
     * T66 / §VII.6.g — submissions owned by a given agent (via
     * {@code flow.turAIAgent}) whose {@code completedAt} predates the cutoff.
     * Feeds the {@code RETAIN_DAYS} branch of the daily submission-retention
     * cleanup job.
     *
     * @since 2026.3.1
     */
    List<TurChatFlowSubmission> findByFlow_TurAIAgent_IdAndCompletedAtBefore(
            String agentId, LocalDateTime cutoff);
}
