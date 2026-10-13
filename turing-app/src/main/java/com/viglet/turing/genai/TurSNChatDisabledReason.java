/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.genai;

/**
 * Reasons a site's chat may be unavailable. {@code GET /api/sn/{site}/chat/enabled}
 * answers the name, and the frontend uses it to show an admin-facing tooltip
 * explaining what to configure.
 *
 * <p>Moved out of {@code TurSNSiteGenAiAPI} in T1087 so the route resolver that
 * both chat endpoints share can answer it; the names, which are the wire format,
 * did not change.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.17
 */
public enum TurSNChatDisabledReason {
    /** Chat is fully wired up — the AI Mode button should render. */
    NONE,
    /** The SN site has no {@code TurSNSiteGenAi} record bound. */
    NO_GENAI,
    /** {@code TurSNSiteGenAi} exists but no AI agent is linked. */
    NO_AGENT,
    /** Linked agent has {@code enabled = 0}. */
    AGENT_DISABLED,
    /** Linked agent has {@code ragEnabled = false}. */
    RAG_DISABLED,
    /** Linked agent has no LLM instance. */
    MISSING_LLM,
    /** Linked agent has no embedding model instance. */
    MISSING_EMBEDDING,
    /** Linked agent has no vector store instance. */
    MISSING_STORE,
    /**
     * T790 / §LIV.1 (Block BF) — the site runs in {@code VECTORLESS_STRUCTURED}
     * mode (the catalog copilot, which needs neither an embedding model nor a
     * vector store) but no usable <em>default LLM</em> is configured, so the
     * copilot cannot answer. The vectorless readiness path reports this instead
     * of {@code MISSING_EMBEDDING}/{@code MISSING_STORE}.
     */
    MISSING_DEFAULT_LLM
}
