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

import java.time.LocalDateTime;

/**
 * Domain entity for an immutable record of a finished chat-flow run —
 * append-only history written when the runtime state transitions to a
 * terminal node (real end node or the synthetic {@code "__abandoned__"}
 * marker). Free of JPA / Jackson annotations and immutable.
 *
 * <p>Distinct from {@link TurChatFlowStateDomain}, which tracks the
 * <em>in-flight</em> state of a single conversation+flow pair and is
 * unique on {@code (conversationId, flowId)}; submissions are append-only
 * and survive re-triggers (e.g. {@code triggerMode = ALWAYS}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurChatFlowSubmissionDomain(
        String id,
        String flowId,
        String conversationId,
        String variablesJson,
        LocalDateTime completedAt,
        String userId,
        String endNodeId) {

    /** Synthetic marker recorded as {@code endNodeId} when the user quits a flow without an end node. */
    public static final String ABANDONED_END_NODE_ID = "__abandoned__";

    /** True when this submission was abandoned rather than completing on a real end node. */
    public boolean wasAbandoned() {
        return ABANDONED_END_NODE_ID.equals(endNodeId);
    }
}
