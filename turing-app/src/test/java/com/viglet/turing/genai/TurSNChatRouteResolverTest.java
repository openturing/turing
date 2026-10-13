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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurSNChatRouteResolver.Route;
import com.viglet.turing.genai.catalog.TurCatalogCopilotService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

/**
 * T1087 — one decision answers both "is the chat ready?" and "how does it
 * answer?", so for every knowledge-base mode, ready means a route that answers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNChatRouteResolverTest {

    @Mock
    private TurDefaultAgentResolver turDefaultAgentResolver;
    @Mock
    private TurCatalogCopilotService catalogCopilotService;
    @InjectMocks
    private TurSNChatRouteResolver resolver;

    private static TurAIAgent completeAgent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        agent.setLlmInstances(new HashSet<>(List.of(new TurLLMInstance())));
        agent.setTurEmbeddingModelInstance(new TurEmbeddingModel());
        agent.setTurStoreInstance(new TurStoreInstance());
        return agent;
    }

    private static TurSNSiteGenAi genAi(TurSNKnowledgeBaseMode mode, TurAIAgent agent) {
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setKnowledgeBaseMode(mode);
        genAi.setTurAIAgent(agent);
        return genAi;
    }

    @ParameterizedTest
    @EnumSource(TurSNKnowledgeBaseMode.class)
    @DisplayName("For every mode, a fully configured site is ready on a route that answers")
    void readyAlwaysMeansARouteThatAnswers(TurSNKnowledgeBaseMode mode) {
        TurAIAgent agent = completeAgent();
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(agent);
        lenient().when(catalogCopilotService.isAvailable()).thenReturn(true);

        TurSNChatRouteResolver.Decision decision = resolver.resolve(genAi(mode, agent));

        assertThat(decision.available()).isTrue();
        assertThat(decision.reason()).isEqualTo(TurSNChatDisabledReason.NONE);
        assertThat(decision.route()).isEqualTo(mode.needsVectorSetup() ? Route.RAG : Route.COPILOT);
    }

    @ParameterizedTest
    @EnumSource(TurSNKnowledgeBaseMode.class)
    @DisplayName("For every mode, a site with nothing configured is unavailable and says why")
    void unconfiguredIsUnavailableWithAReason(TurSNKnowledgeBaseMode mode) {
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(null);
        lenient().when(catalogCopilotService.isAvailable()).thenReturn(false);

        TurSNChatRouteResolver.Decision decision = resolver.resolve(genAi(mode, null));

        assertThat(decision.route()).isEqualTo(Route.UNAVAILABLE);
        assertThat(decision.reason()).isEqualTo(mode.needsVectorSetup()
                ? TurSNChatDisabledReason.NO_AGENT
                : TurSNChatDisabledReason.MISSING_DEFAULT_LLM);
    }

    @Test
    @DisplayName("A vectorless site never needs an agent, an embedding model or a store")
    void vectorlessNeedsOnlyTheDefaultLlm() {
        lenient().when(catalogCopilotService.isAvailable()).thenReturn(true);

        TurSNChatRouteResolver.Decision decision = resolver.resolve(
                genAi(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED, null));

        assertThat(decision.route()).isEqualTo(Route.COPILOT);
    }

    @Test
    @DisplayName("A site with no GenAI binding and no default agent reports NO_GENAI")
    void noBindingAndNoDefaultAgent() {
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(null);

        assertThat(resolver.resolve(null).reason()).isEqualTo(TurSNChatDisabledReason.NO_GENAI);
    }

    @Test
    @DisplayName("The vector walk names the first missing piece")
    void vectorWalkNamesTheFirstMissingPiece() {
        TurAIAgent agent = completeAgent();
        agent.setTurStoreInstance(null);
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(agent);

        assertThat(resolver.resolve(genAi(TurSNKnowledgeBaseMode.VECTOR, agent)).reason())
                .isEqualTo(TurSNChatDisabledReason.MISSING_STORE);
    }
}
