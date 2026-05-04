/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.agent;

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

import com.viglet.turing.domain.agent.TurChatFlowDomain;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

/** Unit tests for {@link TurChatFlowRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurChatFlowRepositoryAdapterTest {

    @Mock
    private TurChatFlowRepository turChatFlowRepository;

    private TurChatFlowRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurChatFlowRepositoryAdapter(turChatFlowRepository,
                Mappers.getMapper(TurChatFlowDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndAgentId() {
        TurChatFlow entity = buildFlow("f-1", "refunds", 1, "agent-1");
        entity.setDefinitionJson("{\"nodes\":[]}");
        entity.setTriggerDescription("when user asks for a refund");
        when(turChatFlowRepository.findById("f-1")).thenReturn(Optional.of(entity));

        TurChatFlowDomain domain = adapter.findById("f-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("f-1");
        assertThat(domain.name()).isEqualTo("refunds");
        assertThat(domain.agentId()).isEqualTo("agent-1");
        assertThat(domain.definitionJson()).isEqualTo("{\"nodes\":[]}");
        assertThat(domain.guardrailMethod()).isEqualTo(TurChatFlowGuardrailMethod.HEURISTIC);
        assertThat(domain.canAutoTrigger()).isTrue();
        assertThat(domain.isEnabled()).isTrue();
    }

    @Test
    void findByAgentIdOrderByNameDelegatesToRepository() {
        when(turChatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(buildFlow("a", "alpha", 1, "agent-1"),
                        buildFlow("b", "beta", 1, "agent-1")));

        List<TurChatFlowDomain> result = adapter.findByAgentIdOrderByName("agent-1");

        assertThat(result).extracting(TurChatFlowDomain::id).containsExactly("a", "b");
    }

    @Test
    void findEnabledByAgentIdFiltersByEnabledFlag() {
        when(turChatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(buildFlow("a", "alpha", 1, "agent-1"),
                        buildFlow("b", "beta", 0, "agent-1"),
                        buildFlow("c", "gamma", 1, "agent-1")));

        List<TurChatFlowDomain> result = adapter.findEnabledByAgentId("agent-1");

        assertThat(result).extracting(TurChatFlowDomain::id).containsExactly("a", "c");
    }

    private static TurChatFlow buildFlow(String id, String name, int enabled, String agentId) {
        TurChatFlow entity = new TurChatFlow();
        entity.setId(id);
        entity.setName(name);
        entity.setEnabled(enabled);
        entity.setGuardrailMethod(TurChatFlowGuardrailMethod.HEURISTIC);
        entity.setTriggerMode(TurChatFlowTriggerMode.ONCE);
        TurAIAgent agent = new TurAIAgent();
        agent.setId(agentId);
        entity.setTurAIAgent(agent);
        return entity;
    }
}
