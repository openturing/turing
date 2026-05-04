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
package com.viglet.turing.domain.sn;

/**
 * Domain entity for the GenAI binding of an SN site — delegates LLM,
 * embedding model, vector store and the RAG-enabled flag to a referenced
 * {@code TurAIAgent}; only the site-specific {@code sitePrompt} (input to
 * the summary / insights LLM call, distinct from the agent's chat system
 * prompt) lives here.
 *
 * <p>Free of JPA / Jackson annotations and immutable. The agent is
 * referenced by ID; resolve through
 * {@link com.viglet.turing.domain.agent.TurAIAgentRepositoryPort} when
 * the full agent is needed. A {@code null} {@code agentId} effectively
 * disables GenAI for the site.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteGenAiDomain(
        String id,
        String agentId,
        String sitePrompt) {

    /** True when GenAI is configured for the site (i.e. an agent is bound). */
    public boolean isConfigured() {
        return agentId != null && !agentId.isBlank();
    }
}
