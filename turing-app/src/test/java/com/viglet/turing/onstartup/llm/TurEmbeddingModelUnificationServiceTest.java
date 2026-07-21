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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurEmbeddingModelUnificationServiceTest {

    @Mock
    private TurEmbeddingModelRepository embeddingModelRepository;
    @Mock
    private TurLLMInstanceRepository instanceRepository;
    @Mock
    private TurLLMVendorRepository vendorRepository;

    @InjectMocks
    private TurEmbeddingModelUnificationService service;

    private TurLLMInstance capturedSave() {
        ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
        verify(instanceRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void cloudEmbeddingBecomesInstanceCopyingParentConnection() {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("OPENAI");
        TurLLMInstance parent = new TurLLMInstance();
        parent.setId("parent-1");
        parent.setUrl("https://api.openai.com");
        parent.setApiKeyEncrypted("enc-key");
        parent.setTurLLMVendor(vendor);

        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId("emb-1");
        em.setModelName("text-embedding-3-small");
        em.setModelReference("text-embedding-3-small");
        em.setProviderType("OPENAI");
        em.setEnabled(1);
        em.setTurLLMInstance(parent);

        when(embeddingModelRepository.findAll()).thenReturn(List.of(em));
        when(instanceRepository.existsById("emb-1")).thenReturn(false);

        int migrated = service.reconcile();

        assertThat(migrated).isEqualTo(1);
        TurLLMInstance saved = capturedSave();
        assertThat(saved.getId()).isEqualTo("emb-1"); // id-preserving
        assertThat(saved.getTurLLMVendor()).isSameAs(vendor);
        assertThat(saved.getUrl()).isEqualTo("https://api.openai.com");
        assertThat(saved.getApiKeyEncrypted()).isEqualTo("enc-key");
        assertThat(saved.getEmbeddingModelName()).isEqualTo("text-embedding-3-small");
        assertThat(saved.isToolsEnabled()).isFalse();
    }

    @Test
    void localEmbeddingBecomesInstanceWithOnnxVendorAndPaths() {
        TurLLMVendor onnx = new TurLLMVendor();
        onnx.setId("TRANSFORMERS_LOCAL");

        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId("emb-local");
        em.setModelName("bge-small");
        em.setProviderType("TRANSFORMERS_LOCAL");
        em.setModelPath("/models/bge.onnx");
        em.setTokenizerPath("/models/tokenizer.json");
        em.setBatchSize(16);
        em.setEnabled(1);
        // no parent instance (in-process)

        when(embeddingModelRepository.findAll()).thenReturn(List.of(em));
        when(instanceRepository.existsById("emb-local")).thenReturn(false);
        when(vendorRepository.findById("TRANSFORMERS_LOCAL")).thenReturn(Optional.of(onnx));

        service.reconcile();

        TurLLMInstance saved = capturedSave();
        assertThat(saved.getId()).isEqualTo("emb-local");
        assertThat(saved.getTurLLMVendor()).isSameAs(onnx);
        assertThat(saved.getUrl()).isEmpty(); // in-process, no endpoint
        assertThat(saved.getEmbeddingModelName()).isEqualTo("bge-small");
        assertThat(saved.getEmbeddingModelPath()).isEqualTo("/models/bge.onnx");
        assertThat(saved.getEmbeddingTokenizerPath()).isEqualTo("/models/tokenizer.json");
        assertThat(saved.getEmbeddingBatchSize()).isEqualTo(16);
    }

    @Test
    void alreadyMigratedIsSkipped() {
        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId("emb-1");
        when(embeddingModelRepository.findAll()).thenReturn(List.of(em));
        when(instanceRepository.existsById("emb-1")).thenReturn(true);

        int migrated = service.reconcile();

        assertThat(migrated).isZero();
        verify(instanceRepository, never()).save(any());
    }

    @Test
    void unresolvableVendorIsSkipped() {
        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId("emb-x");
        em.setModelName("mystery");
        em.setProviderType("UNKNOWN_MODE");
        when(embeddingModelRepository.findAll()).thenReturn(List.of(em));
        when(instanceRepository.existsById("emb-x")).thenReturn(false);
        when(vendorRepository.findById("UNKNOWN_MODE")).thenReturn(Optional.empty());

        int migrated = service.reconcile();

        assertThat(migrated).isZero();
        verify(instanceRepository, never()).save(any());
    }
}
