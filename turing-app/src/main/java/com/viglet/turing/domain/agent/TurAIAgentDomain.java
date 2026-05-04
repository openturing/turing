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

import java.util.Set;

/**
 * Domain entity for an AI Agent — the orchestrator that combines a system
 * prompt with one or more LLM instances, MCP servers, custom tools, and
 * (optionally) a vector store + embedding model for RAG.
 *
 * <p>Free of JPA / Jackson annotations and immutable. All neighbouring
 * aggregates are projected to their IDs ({@code llmInstanceIds},
 * {@code mcpServerIds}, {@code customToolIds},
 * {@code embeddingModelInstanceId}, {@code storeInstanceId}); resolve them
 * through the corresponding ports when the full aggregate is needed.
 *
 * <p>The set-of-ID projections are exposed as immutable copies — the
 * mapper produces unmodifiable sets so callers cannot mutate the agent's
 * configured memberships through this record.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurAIAgentDomain(
        String id,
        String title,
        String description,
        String icon,
        String systemPrompt,
        String systemPromptMetaPrompt,
        int enabled,
        boolean ragEnabled,
        String nativeTools,
        Set<String> llmInstanceIds,
        Set<String> mcpServerIds,
        Set<String> customToolIds,
        String embeddingModelInstanceId,
        String storeInstanceId) {

    /** True when the agent is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }

    /** True when this agent allows the given LLM instance to be used at runtime. */
    public boolean allowsLlmInstance(String llmInstanceId) {
        return llmInstanceIds != null && llmInstanceIds.contains(llmInstanceId);
    }
}
