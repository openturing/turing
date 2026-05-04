/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

import com.viglet.turing.domain.intent.TurIntentActionDomain;
import com.viglet.turing.domain.intent.TurIntentDomain;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.model.intent.TurIntentAction;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;

/** Unit tests for {@link TurIntentRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurIntentRepositoryAdapterTest {

    @Mock
    private TurIntentRepository turIntentRepository;

    private TurIntentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurIntentRepositoryAdapter(turIntentRepository,
                Mappers.getMapper(TurIntentDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndFullActions() {
        TurIntent entity = buildIntent("i-1", "Refunds", 1, "agent-1");
        entity.setActions(setOfActions("a-1", "a-2"));
        when(turIntentRepository.findById("i-1")).thenReturn(Optional.of(entity));

        TurIntentDomain domain = adapter.findById("i-1").orElseThrow();

        assertThat(domain.title()).isEqualTo("Refunds");
        assertThat(domain.agentId()).isEqualTo("agent-1");
        assertThat(domain.isEnabled()).isTrue();
        assertThat(domain.actions()).extracting(TurIntentActionDomain::id)
                .containsExactlyInAnyOrder("a-1", "a-2");
        assertThat(domain.actions()).extracting(TurIntentActionDomain::label)
                .containsExactlyInAnyOrder("label-a-1", "label-a-2");
    }

    @Test
    void findEnabledByAgentIdOrderBySortOrderPassesEnabledFlag() {
        when(turIntentRepository.findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc("agent-1", 1))
                .thenReturn(List.of(buildIntent("i-1", "Refunds", 1, "agent-1")));

        adapter.findEnabledByAgentIdOrderBySortOrder("agent-1");

        verify(turIntentRepository)
                .findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(eq("agent-1"), eq(1));
    }

    @Test
    void findEnabledOrderBySortOrderDelegatesToRepository() {
        when(turIntentRepository.findByEnabledOrderBySortOrderAsc(1))
                .thenReturn(List.of(buildIntent("i-1", "Refunds", 1, "agent-1")));

        adapter.findEnabledOrderBySortOrder();

        verify(turIntentRepository).findByEnabledOrderBySortOrderAsc(1);
    }

    private static TurIntent buildIntent(String id, String title, int enabled, String agentId) {
        TurIntent entity = new TurIntent();
        entity.setId(id);
        entity.setTitle(title);
        entity.setEnabled(enabled);
        entity.setSortOrder(0);
        TurAIAgent agent = new TurAIAgent();
        agent.setId(agentId);
        entity.setTurAIAgent(agent);
        return entity;
    }

    private static Set<TurIntentAction> setOfActions(String... ids) {
        Set<TurIntentAction> set = new HashSet<>();
        for (String id : ids) {
            TurIntentAction action = new TurIntentAction();
            action.setId(id);
            action.setLabel("label-" + id);
            action.setPrompt("prompt-" + id);
            set.add(action);
        }
        return set;
    }
}
