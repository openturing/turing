/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.embedding.TurEmbeddingModelDomain;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;

/** Unit tests for {@link TurEmbeddingModelRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurEmbeddingModelRepositoryAdapterTest {

    @Mock
    private TurEmbeddingModelRepository turEmbeddingModelRepository;

    private TurEmbeddingModelRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurEmbeddingModelRepositoryAdapter(turEmbeddingModelRepository,
                Mappers.getMapper(TurEmbeddingModelDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithLlmInstanceIdProjected() {
        TurEmbeddingModel entity = buildEntity("emb-1", "text-embedding-3-small", 1);
        TurLLMInstance llm = new TurLLMInstance();
        llm.setId("llm-1");
        entity.setTurLLMInstance(llm);
        when(turEmbeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(entity));

        Optional<TurEmbeddingModelDomain> result = adapter.findById("emb-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("emb-1");
        assertThat(result.get().modelName()).isEqualTo("text-embedding-3-small");
        assertThat(result.get().llmInstanceId()).isEqualTo("llm-1");
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    void findByIdLeavesLlmInstanceIdNullWhenAssociationMissing() {
        when(turEmbeddingModelRepository.findById("emb-1"))
                .thenReturn(Optional.of(buildEntity("emb-1", "model", 1)));

        TurEmbeddingModelDomain domain = adapter.findById("emb-1").orElseThrow();

        assertThat(domain.llmInstanceId()).isNull();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turEmbeddingModelRepository.findAll()).thenReturn(List.of(buildEntity("a", "A", 1),
                buildEntity("b", "B", 0), buildEntity("c", "C", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurEmbeddingModelDomain::id)
                .containsExactly("a", "c");
    }

    private static TurEmbeddingModel buildEntity(String id, String modelName, int enabled) {
        TurEmbeddingModel entity = new TurEmbeddingModel();
        entity.setId(id);
        entity.setModelName(modelName);
        entity.setProviderType("openai");
        entity.setEnabled(enabled);
        return entity;
    }
}
