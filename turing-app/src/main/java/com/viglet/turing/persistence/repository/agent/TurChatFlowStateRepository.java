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
}
