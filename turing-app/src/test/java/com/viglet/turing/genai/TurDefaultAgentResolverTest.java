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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
}
