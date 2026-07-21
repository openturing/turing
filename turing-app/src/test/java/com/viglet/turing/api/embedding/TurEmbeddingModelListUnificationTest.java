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
package com.viglet.turing.api.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurEmbeddingDimensionService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceModelDiscoveryService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceRepoResolver;
import com.viglet.turing.genai.provider.llm.TurLlmModelDiscoveryService;
import com.viglet.turing.genai.verify.TurModelVerifyService;
import com.viglet.turing.persistence.dto.embedding.TurEmbeddingModelDto;
import com.viglet.turing.persistence.mapper.embedding.TurEmbeddingModelMapper;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

/**
 * T757 — the unified embedding list ({@code GET /api/embedding-model}) merges
 * embedding-capable {@code llm_instance}s with un-shadowed legacy rows.
 */
@ExtendWith(MockitoExtension.class)
class TurEmbeddingModelListUnificationTest {

    @Mock private TurEmbeddingModelRepository turEmbeddingModelRepository;
    @Mock private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock private TurEmbeddingModelMapper turEmbeddingModelMapper;
    @Mock private TurInfraTenantScope tenantScope;
    @Mock private TurHuggingFaceModelDiscoveryService huggingFaceModelDiscoveryService;
    @Mock private TurLlmModelDiscoveryService llmModelDiscoveryService;
    @Mock private TurEmbeddingDimensionService embeddingDimensionService;
    @Mock private TurHuggingFaceRepoResolver huggingFaceRepoResolver;
    @Mock private TurModelVerifyService modelVerifyService;

    @InjectMocks
    private TurEmbeddingModelAPI api;

    private static TurLLMInstance instance(String id, String embeddingModelName) {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("OPENAI");
        TurLLMInstance inst = new TurLLMInstance();
        inst.setId(id);
        inst.setTitle(id);
        inst.setTurLLMVendor(vendor);
        inst.setEmbeddingModelName(embeddingModelName);
        return inst;
    }

    private static TurEmbeddingModel legacy(String id, String modelName) {
        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId(id);
        em.setModelName(modelName);
        return em;
    }

    @SuppressWarnings("unchecked")
    private List<TurEmbeddingModel> captureUnified(List<TurLLMInstance> instances,
            List<TurEmbeddingModel> legacyRows) {
        // visibleList just runs the "all" supplier in this unit context.
        when(tenantScope.visibleList(any(), any()))
                .thenAnswer(inv -> ((Supplier<List<?>>) inv.getArgument(0)).get());
        when(turLLMInstanceRepository.findAll()).thenReturn(instances);
        when(turEmbeddingModelRepository.findAll()).thenReturn(legacyRows);
        ArgumentCaptor<List<TurEmbeddingModel>> captor = ArgumentCaptor.forClass(List.class);
        when(turEmbeddingModelMapper.toDtoList(captor.capture())).thenReturn(List.<TurEmbeddingModelDto>of());

        api.turEmbeddingModelList();
        return captor.getValue();
    }

    @Test
    void mergesEmbeddingCapableInstanceWithUnrelatedLegacyRow() {
        List<TurEmbeddingModel> unified = captureUnified(
                List.of(instance("i1", "text-embedding-3-small")),
                List.of(legacy("L1", "legacy-embed")));

        assertThat(unified).extracting(TurEmbeddingModel::getId).containsExactlyInAnyOrder("i1", "L1");
    }

    @Test
    void instanceShadowsLegacyRowWithSameId() {
        List<TurEmbeddingModel> unified = captureUnified(
                List.of(instance("shared", "text-embedding-3-large")),
                List.of(legacy("shared", "old-name")));

        assertThat(unified).extracting(TurEmbeddingModel::getId).containsExactly("shared");
        // the surviving entry is the instance-derived one (carries modelReference)
        assertThat(unified.get(0).getModelReference()).isEqualTo("text-embedding-3-large");
    }

    @Test
    void chatOnlyInstanceIsExcluded() {
        TurLLMInstance chatOnly = instance("c1", null); // no embedding default → not capable
        List<TurEmbeddingModel> unified = captureUnified(
                List.of(chatOnly),
                List.of(legacy("L1", "legacy-embed")));

        assertThat(unified).extracting(TurEmbeddingModel::getId).containsExactly("L1");
    }

    private TurEmbeddingModel captureGet(String id) {
        ArgumentCaptor<TurEmbeddingModel> captor = ArgumentCaptor.forClass(TurEmbeddingModel.class);
        when(turEmbeddingModelMapper.toDto(captor.capture())).thenReturn(new TurEmbeddingModelDto());
        api.turEmbeddingModelGet(id);
        return captor.getValue();
    }

    @Test
    void getByIdReturnsLegacyRowWhenPresent() {
        when(tenantScope.isVisibleToTenant(any())).thenReturn(true);
        when(turEmbeddingModelRepository.findById("L1"))
                .thenReturn(java.util.Optional.of(legacy("L1", "legacy-embed")));

        assertThat(captureGet("L1").getId()).isEqualTo("L1");
    }

    @Test
    void getByIdFallsBackToInstanceBackedEmbedding() {
        when(tenantScope.isVisibleToTenant(any())).thenReturn(true);
        when(turEmbeddingModelRepository.findById("i1")).thenReturn(java.util.Optional.empty());
        when(turLLMInstanceRepository.findById("i1"))
                .thenReturn(java.util.Optional.of(instance("i1", "text-embedding-3-small")));

        TurEmbeddingModel got = captureGet("i1");
        assertThat(got.getId()).isEqualTo("i1");
        assertThat(got.getModelReference()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void getByIdReturnsEmptyWhenNeitherExists() {
        when(turEmbeddingModelRepository.findById("gone")).thenReturn(java.util.Optional.empty());
        when(turLLMInstanceRepository.findById("gone")).thenReturn(java.util.Optional.empty());

        assertThat(captureGet("gone").getId()).isNull();
    }
}
