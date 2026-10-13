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

package com.viglet.turing.onstartup.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory;
import com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T1088 — the RAG bootstrap converges each piece the default agent lacks, so the
 * seed's own vector store no longer stops the embedding model from being made.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurDefaultRagInfraOnStartupTest {

    @Mock
    private TurEmbeddingModelRepository turEmbeddingModelRepository;
    @Mock
    private TurStoreInstanceRepository turStoreInstanceRepository;
    @Mock
    private TurStoreVendorRepository turStoreVendorRepository;
    @Mock
    private TurAIAgentRepository turAIAgentRepository;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurGlobalSettingsService settings;
    @Mock
    private TurLocalEmbeddingModelFactory localEmbeddingModelFactory;
    @Mock
    private TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory;
    @Mock
    private TurSNSiteContentExchangeService contentExchangeService;

    private TurDefaultRagInfraOnStartup runner;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        // No background reindex in a unit test: the warm-up thread only touches mocks.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("turing.startup.default-rag.reindex", "false");
        runner = new TurDefaultRagInfraOnStartup(environment, turEmbeddingModelRepository,
                turStoreInstanceRepository, turStoreVendorRepository, turAIAgentRepository, turSNSiteRepository,
                settings, localEmbeddingModelFactory, huggingFaceEmbeddingModelFactory, contentExchangeService);
        agent = new TurAIAgent();
        agent.setId("agent-1");
        when(settings.getDefaultAiAgentId()).thenReturn("agent-1");
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
    }

    private static TurStoreInstance luceneStore(String id) {
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("LUCENE");
        TurStoreInstance store = new TurStoreInstance();
        store.setId(id);
        store.setTitle("Default Vector Store");
        store.setTurStoreVendor(vendor);
        store.setEnabled(1);
        return store;
    }

    @Test
    @DisplayName("The seed's own Lucene store is adopted and the missing embedding model is created")
    void seedStoreIsAdoptedAndTheModelCreated() {
        TurStoreInstance seedStore = luceneStore("seed-store");
        when(turEmbeddingModelRepository.count()).thenReturn(0L);
        when(turStoreInstanceRepository.findAll()).thenReturn(List.of(seedStore));

        runner.run(null);

        assertThat(agent.getTurEmbeddingModelInstance()).isNotNull();
        assertThat(agent.getTurEmbeddingModelInstance().getProviderType())
                .isEqualTo(TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE);
        assertThat(agent.getTurStoreInstance()).isSameAs(seedStore);
        assertThat(agent.isRagEnabled()).isTrue();
        verify(turEmbeddingModelRepository).save(any(TurEmbeddingModel.class));
        verify(turStoreInstanceRepository, never()).save(any());
        verify(settings).updateDefaultEmbeddingStoreId("seed-store");
        verify(turAIAgentRepository).save(agent);
    }

    @Test
    @DisplayName("A fresh install gets both pieces created")
    void freshInstallCreatesBoth() {
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("LUCENE");
        when(turEmbeddingModelRepository.count()).thenReturn(0L);
        when(turStoreInstanceRepository.findAll()).thenReturn(List.of());
        when(turStoreInstanceRepository.count()).thenReturn(0L);
        when(turStoreVendorRepository.findById("LUCENE")).thenReturn(Optional.of(vendor));

        runner.run(null);

        assertThat(agent.getTurEmbeddingModelInstance()).isNotNull();
        assertThat(agent.getTurStoreInstance().getTurStoreVendor()).isSameAs(vendor);
        assertThat(agent.isRagEnabled()).isTrue();
        verify(turStoreInstanceRepository).save(any(TurStoreInstance.class));
    }

    @Test
    @DisplayName("Global defaults are adopted rather than anything created")
    void globalDefaultsAreAdopted() {
        TurEmbeddingModel model = new TurEmbeddingModel();
        model.setEnabled(1);
        TurStoreInstance store = luceneStore("store-1");
        when(settings.getDefaultEmbeddingModelId()).thenReturn("model-1");
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(turEmbeddingModelRepository.findById("model-1")).thenReturn(Optional.of(model));
        when(turStoreInstanceRepository.findById("store-1")).thenReturn(Optional.of(store));

        runner.run(null);

        assertThat(agent.getTurEmbeddingModelInstance()).isSameAs(model);
        assertThat(agent.getTurStoreInstance()).isSameAs(store);
        verify(turEmbeddingModelRepository, never()).save(any());
        verify(turStoreInstanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing models with no default are not picked among, and the agent is left as it was")
    void existingModelsWithoutADefaultAreNotPickedAmong() {
        when(turEmbeddingModelRepository.count()).thenReturn(2L);

        runner.run(null);

        assertThat(agent.getTurEmbeddingModelInstance()).isNull();
        assertThat(agent.isRagEnabled()).isFalse();
        verify(turAIAgentRepository, never()).save(any());
    }

    @Test
    @DisplayName("An agent that already has a model and a store is left alone")
    void wiredAgentIsLeftAlone() {
        agent.setTurEmbeddingModelInstance(new TurEmbeddingModel());
        agent.setTurStoreInstance(luceneStore("mine"));
        lenient().when(turEmbeddingModelRepository.count()).thenReturn(0L);

        runner.run(null);

        verify(turAIAgentRepository, never()).save(any());
        verify(settings, never()).updateDefaultEmbeddingStoreId(any());
    }
}
