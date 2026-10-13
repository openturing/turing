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

import com.viglet.turing.genai.catalog.TurCatalogCopilotService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

/**
 * Decides how a site's chat answers (T1087): through RAG over the site's
 * vectors, through the catalog copilot, or not at all and why.
 *
 * <p>The one answer to that question. {@code /chat/enabled} used to decide it in
 * the controller while {@code /chat/conversation} decided again in its own code,
 * so a vectorless site was reported ready and then answered "AI configuration is
 * not enabled". Both endpoints now read this, so a knowledge-base mode added
 * later is taught to one place and the two cannot disagree.
 *
 * <p>The check is config-only: no model is instantiated, so broken credentials
 * do not hide the AI Mode button; they surface on the real chat call instead.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSNChatRouteResolver {

    /** How a site's chat answers. */
    public enum Route {
        /** Retrieval over the site's vectors, through its (or the default) AI agent. */
        RAG,
        /** The catalog copilot over the declared field schema, with no embeddings. */
        COPILOT,
        /** The chat cannot answer; {@link Decision#reason()} says why. */
        UNAVAILABLE
    }

    /** The route, and the reason when it is {@link Route#UNAVAILABLE} ({@code NONE} otherwise). */
    public record Decision(Route route, TurSNChatDisabledReason reason) {

        public boolean available() {
            return route != Route.UNAVAILABLE;
        }

        static Decision unavailable(TurSNChatDisabledReason reason) {
            return new Decision(Route.UNAVAILABLE, reason);
        }
    }

    private final TurDefaultAgentResolver turDefaultAgentResolver;
    private final TurCatalogCopilotService catalogCopilotService;

    public TurSNChatRouteResolver(TurDefaultAgentResolver turDefaultAgentResolver,
            TurCatalogCopilotService catalogCopilotService) {
        this.turDefaultAgentResolver = turDefaultAgentResolver;
        this.catalogCopilotService = catalogCopilotService;
    }

    /**
     * @param genAi the site's GenAI binding; {@code null} for a site with none,
     *              which still chats through the global Default AI Agent (T622)
     */
    public Decision resolve(TurSNSiteGenAi genAi) {
        TurSNKnowledgeBaseMode mode = genAi != null && genAi.getKnowledgeBaseMode() != null
                ? genAi.getKnowledgeBaseMode()
                : TurSNKnowledgeBaseMode.VECTOR;
        // T790 — a vectorless site answers through the catalog copilot, which needs
        // a usable default LLM and neither an embedding model nor a vector store.
        // HYBRID keeps the vector walk below: it is a superset that also runs the copilot.
        if (!mode.needsVectorSetup()) {
            return catalogCopilotService.isAvailable()
                    ? new Decision(Route.COPILOT, TurSNChatDisabledReason.NONE)
                    : Decision.unavailable(TurSNChatDisabledReason.MISSING_DEFAULT_LLM);
        }
        // T622 — a site with no agent of its own resolves to the global Default
        // AI Agent when one is set (fail-open); otherwise the classic reasons.
        TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(genAi);
        TurSNChatDisabledReason reason = agentReadiness(genAi, agent);
        return reason == TurSNChatDisabledReason.NONE
                ? new Decision(Route.RAG, TurSNChatDisabledReason.NONE)
                : Decision.unavailable(reason);
    }

    private static TurSNChatDisabledReason agentReadiness(TurSNSiteGenAi genAi, TurAIAgent agent) {
        if (agent == null) {
            return genAi == null ? TurSNChatDisabledReason.NO_GENAI : TurSNChatDisabledReason.NO_AGENT;
        }
        if (agent.getEnabled() != 1) {
            return TurSNChatDisabledReason.AGENT_DISABLED;
        }
        if (!agent.isRagEnabled()) {
            return TurSNChatDisabledReason.RAG_DISABLED;
        }
        if (agent.getLlmInstances() == null || agent.getLlmInstances().isEmpty()) {
            return TurSNChatDisabledReason.MISSING_LLM;
        }
        if (agent.getTurEmbeddingModelInstance() == null) {
            return TurSNChatDisabledReason.MISSING_EMBEDDING;
        }
        if (agent.getTurStoreInstance() == null) {
            return TurSNChatDisabledReason.MISSING_STORE;
        }
        return TurSNChatDisabledReason.NONE;
    }
}
