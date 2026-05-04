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

import com.viglet.turing.domain.agent.TurChatFlowSubmissionDomain;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;

/** Unit tests for {@link TurChatFlowSubmissionRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurChatFlowSubmissionRepositoryAdapterTest {

    @Mock
    private TurChatFlowSubmissionRepository turChatFlowSubmissionRepository;

    private TurChatFlowSubmissionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurChatFlowSubmissionRepositoryAdapter(turChatFlowSubmissionRepository,
                Mappers.getMapper(TurChatFlowSubmissionDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndFlowId() {
        when(turChatFlowSubmissionRepository.findById("sub-1"))
                .thenReturn(Optional.of(buildSubmission("sub-1", "f-1", "conv-1", "end-real")));

        TurChatFlowSubmissionDomain domain = adapter.findById("sub-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("sub-1");
        assertThat(domain.flowId()).isEqualTo("f-1");
        assertThat(domain.conversationId()).isEqualTo("conv-1");
        assertThat(domain.endNodeId()).isEqualTo("end-real");
        assertThat(domain.wasAbandoned()).isFalse();
    }

    @Test
    void wasAbandonedDetectsTheSyntheticMarker() {
        when(turChatFlowSubmissionRepository.findById("sub-1"))
                .thenReturn(Optional.of(
                        buildSubmission("sub-1", "f-1", "conv-1",
                                TurChatFlowSubmissionDomain.ABANDONED_END_NODE_ID)));

        assertThat(adapter.findById("sub-1").orElseThrow().wasAbandoned()).isTrue();
    }

    @Test
    void findByFlowIdOrderByCompletedAtDescDelegatesToRepository() {
        when(turChatFlowSubmissionRepository.findByFlow_IdOrderByCompletedAtDesc("f-1"))
                .thenReturn(List.of(buildSubmission("sub-1", "f-1", "conv-1", "end-real"),
                        buildSubmission("sub-2", "f-1", "conv-2", "end-real")));

        assertThat(adapter.findByFlowIdOrderByCompletedAtDesc("f-1"))
                .extracting(TurChatFlowSubmissionDomain::id)
                .containsExactly("sub-1", "sub-2");
    }

    private static TurChatFlowSubmission buildSubmission(String id, String flowId,
            String conversationId, String endNodeId) {
        TurChatFlowSubmission entity = new TurChatFlowSubmission();
        entity.setId(id);
        entity.setConversationId(conversationId);
        entity.setEndNodeId(endNodeId);
        entity.setCompletedAt(LocalDateTime.now());
        TurChatFlow flow = new TurChatFlow();
        flow.setId(flowId);
        entity.setFlow(flow);
        return entity;
    }
}
