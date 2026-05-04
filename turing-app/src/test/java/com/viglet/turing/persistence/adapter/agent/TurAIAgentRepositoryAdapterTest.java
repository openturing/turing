/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.agent.TurAIAgentDomain;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

/**
 * Unit tests for {@link TurAIAgentRepositoryAdapter}, covering the
 * many-to-many to set-of-IDs projection and the unmodifiable-set guarantee.
 */
@ExtendWith(MockitoExtension.class)
class TurAIAgentRepositoryAdapterTest {

    @Mock
    private TurAIAgentRepository turAIAgentRepository;

    private TurAIAgentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurAIAgentRepositoryAdapter(turAIAgentRepository,
                Mappers.getMapper(TurAIAgentDomainMapper.class));
    }

    @Test
    void findByIdReturnsMappedDomainWithLlmInstanceIds() {
        TurAIAgent entity = buildAgent("agent-1", "Support", 1);
        entity.setLlmInstances(setOfLlms("llm-1", "llm-2"));
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(entity));

        Optional<TurAIAgentDomain> result = adapter.findById("agent-1");

        assertThat(result).isPresent();
        assertThat(result.get().llmInstanceIds()).containsExactlyInAnyOrder("llm-1", "llm-2");
        assertThat(result.get().mcpServerIds()).isEmpty();
        assertThat(result.get().customToolIds()).isEmpty();
    }

    @Test
    void findByIdProjectsManyToOneRefsAsIds() {
        TurAIAgent entity = buildAgent("agent-1", "RAG", 1);
        TurEmbeddingModel embedding = new TurEmbeddingModel();
        embedding.setId("emb-1");
        entity.setTurEmbeddingModelInstance(embedding);
        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1");
        entity.setTurStoreInstance(store);
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(entity));

        TurAIAgentDomain domain = adapter.findById("agent-1").orElseThrow();

        assertThat(domain.embeddingModelInstanceId()).isEqualTo("emb-1");
        assertThat(domain.storeInstanceId()).isEqualTo("store-1");
    }

    @Test
    void findByIdEmptyDelegatesEmpty() {
        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findAllEnabledFiltersByEnabledFlag() {
        when(turAIAgentRepository.findAll()).thenReturn(List.of(buildAgent("a", "A", 1),
                buildAgent("b", "B", 0), buildAgent("c", "C", 1)));

        assertThat(adapter.findAllEnabled()).extracting(TurAIAgentDomain::id).containsExactly("a",
                "c");
    }

    @Test
    void llmInstanceIdsSetIsUnmodifiable() {
        TurAIAgent entity = buildAgent("agent-1", "X", 1);
        entity.setLlmInstances(setOfLlms("llm-1"));
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(entity));

        Set<String> llmInstanceIds = adapter.findById("agent-1").orElseThrow().llmInstanceIds();

        // Caller must not be able to mutate the agent's configured memberships.
        assertThatThrownBy(() -> llmInstanceIds.add("smuggled"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TurAIAgent buildAgent(String id, String title, int enabled) {
        TurAIAgent entity = new TurAIAgent();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        return entity;
    }

    private static Set<TurLLMInstance> setOfLlms(String... ids) {
        Set<TurLLMInstance> set = new HashSet<>();
        for (String id : ids) {
            TurLLMInstance llm = new TurLLMInstance();
            llm.setId(id);
            set.add(llm);
        }
        return set;
    }
}
