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
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory;
import com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T756 — the unified-first embedding resolution in {@link TurRagContextBuilder}:
 * an embedding-capable {@code llm_instance} (T755-migrated, same id) is adapted
 * into the {@link TurEmbeddingModel} shape; the legacy table is the fallback.
 */
@ExtendWith(MockitoExtension.class)
class TurRagContextBuilderEmbeddingResolutionTest {

    @Mock private TurLlmModelFactory llmModelFactory;
    @Mock private TurLocalEmbeddingModelFactory localEmbeddingModelFactory;
    @Mock private TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory;
    @Mock private TurGenAiStoreProviderFactory storeProviderFactory;
    @Mock private TurSecretCryptoService secretCryptoService;
    @Mock private TurGlobalSettingsService globalSettingsService;
    @Mock private TurEmbeddingModelRepository embeddingModelRepository;
    @Mock private TurLLMInstanceRepository instanceRepository;
    @Mock private TurStoreInstanceRepository storeInstanceRepository;

    @InjectMocks
    private TurRagContextBuilder builder;

    private static TurLLMInstance instance(String id, String vendorId) {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId(vendorId);
        TurLLMInstance inst = new TurLLMInstance();
        inst.setId(id);
        inst.setTurLLMVendor(vendor);
        return inst;
    }

    @Test
    void cloudInstanceIsAdaptedWithItselfAsConnection() {
        TurLLMInstance inst = instance("e1", "OPENAI");
        inst.setUrl("https://api.openai.com");
        inst.setApiKeyEncrypted("enc");
        inst.setEmbeddingModelName("text-embedding-3-small");
        when(instanceRepository.findById("e1")).thenReturn(Optional.of(inst));

        TurEmbeddingModel em = builder.resolveEmbeddingEntity("e1");

        assertThat(em).isNotNull();
        assertThat(em.getId()).isEqualTo("e1");
        assertThat(em.getProviderType()).isEqualTo("OPENAI");
        assertThat(em.getModelReference()).isEqualTo("text-embedding-3-small");
        // cloud: the unified instance itself is the connection (url/key/vendor)
        assertThat(em.getTurLLMInstance()).isSameAs(inst);
    }

    @Test
    void localInstanceIsAdaptedWithOnnxPathsAndNoParent() {
        TurLLMInstance inst = instance("e2", TurLocalEmbeddingModelFactory.PROVIDER_TYPE);
        inst.setEmbeddingModelName("bge-small");
        inst.setEmbeddingModelPath("/m/bge.onnx");
        inst.setEmbeddingTokenizerPath("/m/tok.json");
        inst.setEmbeddingBatchSize(8);
        when(instanceRepository.findById("e2")).thenReturn(Optional.of(inst));

        TurEmbeddingModel em = builder.resolveEmbeddingEntity("e2");

        assertThat(em.getProviderType()).isEqualTo("TRANSFORMERS_LOCAL");
        assertThat(em.getModelPath()).isEqualTo("/m/bge.onnx");
        assertThat(em.getTokenizerPath()).isEqualTo("/m/tok.json");
        assertThat(em.getBatchSize()).isEqualTo(8);
        assertThat(em.getTurLLMInstance()).isNull(); // in-process, no parent
    }

    @Test
    void nonEmbeddingInstanceFallsBackToLegacyTable() {
        // a chat-only instance (no embedding default) must not shadow the legacy row
        TurLLMInstance chatOnly = instance("e3", "OPENAI");
        chatOnly.setModelName("gpt-4o");
        TurEmbeddingModel legacy = new TurEmbeddingModel();
        legacy.setId("e3");
        legacy.setModelName("legacy-embed");
        legacy.setProviderType("OPENAI");
        when(instanceRepository.findById("e3")).thenReturn(Optional.of(chatOnly));
        when(embeddingModelRepository.findById("e3")).thenReturn(Optional.of(legacy));

        TurEmbeddingModel em = builder.resolveEmbeddingEntity("e3");

        assertThat(em).isSameAs(legacy);
    }

    @Test
    void noInstanceFallsBackToLegacyTable() {
        TurEmbeddingModel legacy = new TurEmbeddingModel();
        legacy.setId("e4");
        when(instanceRepository.findById("e4")).thenReturn(Optional.empty());
        when(embeddingModelRepository.findById("e4")).thenReturn(Optional.of(legacy));

        assertThat(builder.resolveEmbeddingEntity("e4")).isSameAs(legacy);
    }

    @Test
    void blankIdIsNull() {
        assertThat(builder.resolveEmbeddingEntity(" ")).isNull();
        assertThat(builder.resolveEmbeddingEntity(null)).isNull();
    }
}
