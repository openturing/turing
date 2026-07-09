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
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Unit tests for {@link TurLlmModelDiscoveryService} — the T625 embedding-model
 * filtering and the {@link TurLlmModelDiscoveryService#isEmbeddingModel} heuristic.
 */
@ExtendWith(MockitoExtension.class)
class TurLlmModelDiscoveryServiceTest {

    @Mock
    private TurGenAiLlmProviderFactory providerFactory;
    @Mock
    private TurLlmModelCatalog catalog;
    @Mock
    private TurLLMVendorRepository vendorRepository;
    @Mock
    private TurLLMInstanceRepository instanceRepository;
    @Mock
    private TurSecretCryptoService secretCryptoService;

    @InjectMocks
    private TurLlmModelDiscoveryService service;

    @Test
    void isEmbeddingModelRecognizesEmbeddingsAndExcludesRerankAndChat() {
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("text-embedding-3-small"))).isTrue();
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                new TurLlmModelOption("embed-v4.0", "Embed v4 (embedding)"))).isTrue();
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("mistral-embed"))).isTrue();
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("voyage-3-large"))).isTrue();
        // rerank is not an embedding model
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("rerank-2"))).isFalse();
        // plain chat models are excluded
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("gpt-4o-mini"))).isFalse();
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(
                TurLlmModelOption.of("claude-sonnet-4-5"))).isFalse();
        assertThat(TurLlmModelDiscoveryService.isEmbeddingModel(null)).isFalse();
    }

    @Test
    void listEmbeddingModelsFiltersLiveListToEmbeddingsOnly() {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("openai");
        when(vendorRepository.findById("openai")).thenReturn(java.util.Optional.of(vendor));

        TurGenAiLlmProvider provider = org.mockito.Mockito.mock(TurGenAiLlmProvider.class);
        when(providerFactory.getProvider(any(TurLLMInstance.class))).thenReturn(provider);
        when(provider.listModels(any(TurLLMInstance.class), any())).thenReturn(List.of(
                TurLlmModelOption.of("gpt-4o"),
                TurLlmModelOption.of("gpt-4o-mini"),
                TurLlmModelOption.of("text-embedding-3-small"),
                TurLlmModelOption.of("text-embedding-3-large")));

        TurLlmModelDiscoveryService.Result result =
                service.listEmbeddingModels("openai", null, "sk-test", null, null);

        assertThat(result.source()).isEqualTo(TurLlmModelDiscoveryService.Source.LIVE);
        assertThat(result.models()).extracting(TurLlmModelOption::id)
                .containsExactly("text-embedding-3-small", "text-embedding-3-large");
    }

    @Test
    void listEmbeddingModelsFallsBackToCatalogWhenLiveHasNoEmbeddings() {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("openai");
        when(vendorRepository.findById("openai")).thenReturn(java.util.Optional.of(vendor));

        TurGenAiLlmProvider provider = org.mockito.Mockito.mock(TurGenAiLlmProvider.class);
        when(providerFactory.getProvider(any(TurLLMInstance.class))).thenReturn(provider);
        // Live list has only chat models → no embeddings survive the filter.
        when(provider.listModels(any(TurLLMInstance.class), any()))
                .thenReturn(List.of(TurLlmModelOption.of("gpt-4o")));
        when(catalog.staticModels("openai")).thenReturn(List.of(
                TurLlmModelOption.of("gpt-4o"),
                new TurLlmModelOption("text-embedding-3-small", "text-embedding-3-small (embedding)")));

        TurLlmModelDiscoveryService.Result result =
                service.listEmbeddingModels("openai", null, "sk-test", null, null);

        assertThat(result.source()).isEqualTo(TurLlmModelDiscoveryService.Source.CATALOG);
        assertThat(result.models()).extracting(TurLlmModelOption::id)
                .containsExactly("text-embedding-3-small");
    }
}
