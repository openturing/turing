/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.agent.TurChatFlowStateDomain;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

/** Unit tests for {@link TurChatFlowStateRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurChatFlowStateRepositoryAdapterTest {

    @Mock
    private TurChatFlowStateRepository turChatFlowStateRepository;

    private TurChatFlowStateRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurChatFlowStateRepositoryAdapter(turChatFlowStateRepository,
                Mappers.getMapper(TurChatFlowStateDomainMapper.class));
    }

    @Test
    void findByConversationIdAndFlowIdProjectsFlowIdFromAssociation() {
        when(turChatFlowStateRepository.findByConversationIdAndFlow_Id("conv-1", "f-1"))
                .thenReturn(Optional.of(buildState("st-1", "conv-1", "f-1", "node-2")));

        TurChatFlowStateDomain domain = adapter
                .findByConversationIdAndFlowId("conv-1", "f-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("st-1");
        assertThat(domain.flowId()).isEqualTo("f-1");
        assertThat(domain.conversationId()).isEqualTo("conv-1");
        assertThat(domain.currentNodeId()).isEqualTo("node-2");
    }

    @Test
    void findByConversationIdAndAgentIdDelegatesToRepository() {
        when(turChatFlowStateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id("conv-1", "agent-1"))
                .thenReturn(List.of(buildState("st-1", "conv-1", "f-1", "n"),
                        buildState("st-2", "conv-1", "f-2", "n")));

        List<TurChatFlowStateDomain> result = adapter
                .findByConversationIdAndAgentId("conv-1", "agent-1");

        assertThat(result).extracting(TurChatFlowStateDomain::flowId).containsExactly("f-1", "f-2");
    }

    @Test
    void findByFlowIdOrderByUpdatedAtDescDelegatesToRepository() {
        when(turChatFlowStateRepository.findByFlow_IdOrderByUpdatedAtDesc("f-1"))
                .thenReturn(List.of(buildState("st-1", "conv-1", "f-1", "n")));

        assertThat(adapter.findByFlowIdOrderByUpdatedAtDesc("f-1")).hasSize(1);
    }

    private static TurChatFlowState buildState(String id, String conversationId, String flowId,
            String currentNodeId) {
        TurChatFlowState entity = new TurChatFlowState();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setCurrentNodeId(currentNodeId);
        entity.setUpdatedAt(LocalDateTime.now());
        TurChatFlow flow = new TurChatFlow();
        flow.setId(flowId);
        entity.setFlow(flow);
        return entity;
    }
}
