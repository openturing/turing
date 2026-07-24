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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for the T622 default-agent fallback.
 */
@ExtendWith(MockitoExtension.class)
class TurDefaultAgentResolverTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurAIAgentRepository turAIAgentRepository;

    @InjectMocks
    private TurDefaultAgentResolver resolver;

    @Test
    void siteOwnAgentWinsAndNoDefaultLookupHappens() {
        TurAIAgent siteAgent = new TurAIAgent();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(siteAgent);

        assertSame(siteAgent, resolver.resolveEffectiveAgent(genAi));
        // The site declares its own agent, so the global default is never touched.
        verifyNoInteractions(globalSettingsService, turAIAgentRepository);
    }

    @Test
    void fallsBackToDefaultWhenBindingHasNoAgent() {
        TurAIAgent defaultAgent = new TurAIAgent();
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("def-1");
        when(turAIAgentRepository.findById("def-1")).thenReturn(Optional.of(defaultAgent));

        assertSame(defaultAgent, resolver.resolveEffectiveAgent(new TurSNSiteGenAi()));
    }

    @Test
    void fallsBackToDefaultWhenBindingIsNull() {
        TurAIAgent defaultAgent = new TurAIAgent();
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("def-1");
        when(turAIAgentRepository.findById("def-1")).thenReturn(Optional.of(defaultAgent));

        assertSame(defaultAgent, resolver.resolveEffectiveAgent(null));
    }

    @Test
    void returnsNullWhenNoDefaultConfigured() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("");

        assertNull(resolver.resolveEffectiveAgent(new TurSNSiteGenAi()));
    }

    @Test
    void returnsNullWhenDefaultAgentIdDangling() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("gone");
        when(turAIAgentRepository.findById("gone")).thenReturn(Optional.empty());

        assertNull(resolver.resolveEffectiveAgent(null));
    }

    private static TurAIAgent ragReadyAgent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        return agent;
    }

    @Test
    void isRagReadyTrueForRagReadyOwnAgent() {
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(ragReadyAgent());

        assertTrue(resolver.isRagReady(genAi));
        verifyNoInteractions(globalSettingsService, turAIAgentRepository);
    }

    @Test
    void isRagReadyFalseWhenOwnAgentDisabled() {
        TurAIAgent agent = ragReadyAgent();
        agent.setEnabled(0);
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(agent);

        assertFalse(resolver.isRagReady(genAi));
    }

    @Test
    void isRagReadyFalseWhenOwnAgentRagOff() {
        TurAIAgent agent = ragReadyAgent();
        agent.setRagEnabled(false);
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(agent);

        assertFalse(resolver.isRagReady(genAi));
    }

    @Test
    void isRagReadyFallsBackToRagReadyDefaultAgent() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("def-1");
        when(turAIAgentRepository.findById("def-1")).thenReturn(Optional.of(ragReadyAgent()));

        assertTrue(resolver.isRagReady(new TurSNSiteGenAi()));
    }

    @Test
    void isRagReadyFalseWhenNoOwnAgentAndNoDefault() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("");

        assertFalse(resolver.isRagReady(null));
    }

    @Test
    void isRagReadyFalseWhenVectorlessStructuredEvenWithRagReadyOwnAgent() {
        // T790 — the site's own agent is RAG-ready, but the site explicitly opts out
        // of vectors via VECTORLESS_STRUCTURED: no vector-dependent surface is ready.
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(ragReadyAgent());
        genAi.setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);

        assertFalse(resolver.isRagReady(genAi));
        // Short-circuits on the mode before ever resolving/looking up an agent.
        verifyNoInteractions(globalSettingsService, turAIAgentRepository);
    }

    @Test
    void isRagReadyFalseWhenVectorlessStructuredEvenWithRagReadyDefaultAgent() {
        // The exact scenario reported: a search-only seed site (no own agent) would
        // inherit the RAG-ready Default AI Agent — but VECTORLESS_STRUCTURED overrides
        // that, so it never becomes RAG-ready (and never embeds). Mode is checked
        // first, so the default lookup is short-circuited.
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);

        assertFalse(resolver.isRagReady(genAi));
        verifyNoInteractions(globalSettingsService, turAIAgentRepository);
    }

    @Test
    void isRagReadyTrueWhenHybridModeAndRagReadyOwnAgent() {
        // HYBRID needs the vector setup, so a RAG-ready agent still qualifies.
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(ragReadyAgent());
        genAi.setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.HYBRID);

        assertTrue(resolver.isRagReady(genAi));
    }

    @Test
    void isDefaultRagReadyTrueWhenDefaultAgentRagReady() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("def-1");
        when(turAIAgentRepository.findById("def-1")).thenReturn(Optional.of(ragReadyAgent()));

        assertTrue(resolver.isDefaultRagReady());
    }

    @Test
    void isDefaultRagReadyFalseWhenNoDefaultConfigured() {
        when(globalSettingsService.getDefaultAiAgentId()).thenReturn("");

        assertFalse(resolver.isDefaultRagReady());
    }
}
