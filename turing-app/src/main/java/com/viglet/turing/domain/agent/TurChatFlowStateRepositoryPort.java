/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.domain.agent;

import java.util.List;
import java.util.Optional;

/**
 * Domain-side port for retrieving {@link TurChatFlowStateDomain} aggregates.
 * Read-only — write paths still go through the JPA repository because the
 * engine touches updatedAt via {@code @PreUpdate}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurChatFlowStateRepositoryPort {

    Optional<TurChatFlowStateDomain> findById(String id);

    /** The unique state for the given conversation+flow pair, when present. */
    Optional<TurChatFlowStateDomain> findByConversationIdAndFlowId(String conversationId,
            String flowId);

    /**
     * All persisted states for a conversation across every flow of the given
     * agent — used by the auto-trigger router to detect in-flight flows and
     * already-completed ONCE flows.
     */
    List<TurChatFlowStateDomain> findByConversationIdAndAgentId(String conversationId,
            String agentId);

    /** All states for a flow, newest first. Feeds the submissions / history page. */
    List<TurChatFlowStateDomain> findByFlowIdOrderByUpdatedAtDesc(String flowId);
}
