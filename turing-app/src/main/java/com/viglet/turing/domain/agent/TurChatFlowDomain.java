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

import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;

/**
 * Domain entity for a chat flow — a scripted multi-step conversation
 * authored in the React Flow editor and scoped to a single AI agent. Free
 * of JPA / Jackson annotations and immutable.
 *
 * <p><strong>Aggregate boundary note.</strong> Chat flows do <em>not</em>
 * have separate node / edge JPA entities — the graph is serialised as JSON
 * into {@code definitionJson} so the runtime engine can deserialise and
 * walk the state machine. Consumers that need to inspect individual nodes
 * parse {@code definitionJson} themselves; this record carries the raw
 * payload unchanged.
 *
 * <p>The owning agent is referenced by ID; resolve through
 * {@link TurAIAgentRepositoryPort} when the full agent is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurChatFlowDomain(
        String id,
        String name,
        String description,
        String definitionJson,
        int enabled,
        TurChatFlowGuardrailMethod guardrailMethod,
        String triggerDescription,
        TurChatFlowTriggerMode triggerMode,
        String agentId) {

    /** True when the flow is enabled for runtime execution (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }

    /**
     * True when the flow may be auto-triggered by the LLM router based on
     * {@code triggerDescription}. A blank trigger description disables
     * auto-trigger; the flow can still be picked manually from the chat
     * dropdown regardless.
     */
    public boolean canAutoTrigger() {
        return triggerDescription != null && !triggerDescription.isBlank();
    }
}
