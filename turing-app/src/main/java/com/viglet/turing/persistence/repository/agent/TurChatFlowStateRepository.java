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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurChatFlowStateRepository extends JpaRepository<TurChatFlowState, String> {

    Optional<TurChatFlowState> findByConversationIdAndFlow_Id(String conversationId, String flowId);

    /**
     * All persisted states for a given conversation across every flow of a
     * given agent. Used by the auto-trigger router to detect (a) any flow
     * still in progress (so the router shouldn't pick a different one) and
     * (b) which flows have already completed (so {@code ONCE} flows are
     * filtered out).
     */
    List<TurChatFlowState> findByConversationIdAndFlow_TurAIAgent_Id(
            String conversationId, String agentId);

    /**
     * All states ever recorded for a flow, newest first. The submissions
     * page filters this list further to keep only the rows that have
     * reached an end node.
     */
    List<TurChatFlowState> findByFlow_IdOrderByUpdatedAtDesc(String flowId);

    /**
     * Child states that point at a given parent state. Used by the engine
     * to detect the active leaf in a sub-flow chain (a state with no
     * descendant) and to clean up orphans when the conversation is reset.
     *
     * @since 2026.2.6
     */
    List<TurChatFlowState> findByParentStateId(String parentStateId);

    /**
     * Every in-progress flow state attached to a conversation, regardless of
     * the agent. Used by the session-variables API to expose live values
     * collected so far when the caller only has the {@code conversationId}.
     *
     * @since 2026.2.7
     */
    List<TurChatFlowState> findByConversationId(String conversationId);

    /**
     * T122 — every state for a given flow whose cursor currently sits on one
     * of {@code nodeIds}. The parked-conversations dashboard resolves the
     * {@code suspend} node ids per flow first (parsing each flow graph once),
     * then loads only the matching states — avoiding a full {@code findAll}
     * scan of the runtime-state table.
     *
     * @since 2026.3.1
     */
    List<TurChatFlowState> findByFlow_IdAndCurrentNodeIdIn(
            String flowId, Collection<String> nodeIds);

    /**
     * T85 — count of in-flight states per cursor node id for a given
     * flow. Mirrors the submission counterpart so the funnel
     * visualization can show both "ended here" and "currently parked
     * here" per node.
     *
     * @since 2026.3.1
     */
    @org.springframework.data.jpa.repository.Query(
            "select s.currentNodeId, count(s) from TurChatFlowState s "
            + "where s.flow.id = :flowId group by s.currentNodeId")
    List<Object[]> countByCurrentNodeForFlow(@org.springframework.data.repository.query.Param("flowId") String flowId);
}
