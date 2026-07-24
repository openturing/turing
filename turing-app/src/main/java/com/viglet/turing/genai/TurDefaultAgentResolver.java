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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T622 — resolves the effective {@link TurAIAgent} for a Semantic Navigation
 * site's chat/RAG, falling back to the global <b>Default AI Agent</b> when the
 * site itself declares none.
 *
 * <p>
 * Historically SN chat used {@code site.getTurSNSiteGenAi().getTurAIAgent()}
 * verbatim: a site with no GenAI binding (or a binding without an agent) simply
 * reported chat as disabled. That is fine for a configured install, but blocks
 * the zero-config public demo, whose seed export is search-only (no per-site
 * agent). This resolver adds a <b>fail-open</b> fallback: when the site has no
 * agent of its own <em>and</em> a global default agent is configured, that
 * default agent (with its embedding model + vector store) is used.
 *
 * <p>
 * The fallback is strictly gated on {@link TurGlobalSettingsService#getDefaultAiAgentId()}
 * being set — on an install with no default agent the behaviour is byte-for-byte
 * unchanged (returns {@code null}, chat stays disabled). The default agent is
 * loaded through {@link TurAIAgentRepository#findById(String)}, which
 * {@code LEFT JOIN FETCH}es the LLM instances, embedding model and store, so it
 * is safe to use on virtual threads without an open session (reindex).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurDefaultAgentResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurAIAgentRepository turAIAgentRepository;

    public TurDefaultAgentResolver(TurGlobalSettingsService globalSettingsService,
            TurAIAgentRepository turAIAgentRepository) {
        this.globalSettingsService = globalSettingsService;
        this.turAIAgentRepository = turAIAgentRepository;
    }

    /**
     * Resolves the agent that should serve chat/RAG for the given GenAI binding:
     * the binding's own agent when present, otherwise the global default agent
     * (or {@code null} when neither exists).
     *
     * @param turSNSiteGenAi the site's GenAI binding (may be {@code null})
     */
    public TurAIAgent resolveEffectiveAgent(TurSNSiteGenAi turSNSiteGenAi) {
        if (turSNSiteGenAi != null && turSNSiteGenAi.getTurAIAgent() != null) {
            return turSNSiteGenAi.getTurAIAgent();
        }
        return resolveDefaultAgent();
    }

    /**
     * @return the configured global Default AI Agent (fully fetched), or
     *         {@code null} when none is set / it no longer exists.
     */
    public TurAIAgent resolveDefaultAgent() {
        String defaultAgentId = globalSettingsService.getDefaultAiAgentId();
        if (!StringUtils.hasText(defaultAgentId)) {
            return null;
        }
        TurAIAgent agent = turAIAgentRepository.findById(defaultAgentId).orElse(null);
        if (agent == null) {
            log.warn("Global default AI agent id '{}' is set but no such agent exists.", defaultAgentId);
        }
        return agent;
    }

    /**
     * Single reusable "is RAG/ANN effectively available for this site" gate,
     * shared by every SN-site feature that needs one (ANN page + search API,
     * the launch-bar chip via {@code /api/features}, etc.) so they never drift
     * apart. Honors the T622 fallback: the site's own agent when present,
     * otherwise the global Default AI Agent.
     *
     * @param turSNSiteGenAi the site's GenAI binding (may be {@code null})
     * @return {@code true} when the effective agent exists, is enabled and has
     *         RAG turned on
     * @since 2026.3.4
     */
    public boolean isRagReady(TurSNSiteGenAi turSNSiteGenAi) {
        // T790 / §LIV.1 (Block BF) — a site explicitly in VECTORLESS_STRUCTURED has
        // no vectors by design, so every vector-dependent surface gated by this
        // method (ANN search API + controller, the launch-bar live-search chip via
        // /api/features) must report "not ready" — even when the effective agent
        // (own or the Default AI Agent fallback) has RAG enabled. VECTOR/HYBRID
        // still qualify.
        return modeNeedsVectors(turSNSiteGenAi) && isAgentRagReady(resolveEffectiveAgent(turSNSiteGenAi));
    }

    /**
     * Whether the site's knowledge-base mode requires the vector setup. A
     * {@code null} binding (or a binding with no explicit mode) defaults to
     * {@code VECTOR}, preserving the pre-T790 behaviour.
     *
     * @since 2026.3.4
     */
    private static boolean modeNeedsVectors(TurSNSiteGenAi turSNSiteGenAi) {
        return turSNSiteGenAi == null || turSNSiteGenAi.getKnowledgeBaseMode() == null
                || turSNSiteGenAi.getKnowledgeBaseMode().needsVectorSetup();
    }

    /**
     * Whether the global Default AI Agent (ignoring any per-site binding) is by
     * itself RAG-ready. Lets a per-site UI decide, without a site in hand,
     * whether a site that declares <em>no</em> agent of its own would still get
     * RAG/ANN through the default fallback.
     *
     * @since 2026.3.4
     */
    public boolean isDefaultRagReady() {
        return isAgentRagReady(resolveDefaultAgent());
    }

    private boolean isAgentRagReady(TurAIAgent agent) {
        return agent != null && agent.getEnabled() == 1 && agent.isRagEnabled();
    }
}
