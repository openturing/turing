/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * T28 / §III.5 Phase B — pins the {@link TurAnalyticsIntentIndexer}'s
 * dispatch through the SE plugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurAnalyticsIntentIndexerTest {

    @Mock
    private TurAnalyticsIntentRepository intentRepository;
    @Mock
    private TurSEInstanceRepository seInstanceRepository;
    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;
    @Mock
    private TurSEInstance seInstance;

    private TurAnalyticsIntentIndexer indexer;

    @BeforeEach
    void setUp() {
        // Empty configured property → resolveSeInstance falls back to first registered SE
        indexer = new TurAnalyticsIntentIndexer(intentRepository, seInstanceRepository,
                agentRepository, pluginFactory, "");
    }

    @Test
    void indexNameStripsDashesAndCapsTo12Chars() {
        String name = TurAnalyticsIntentIndexer.indexNameFor("ab12cd34-aaaa-bbbb-cccc-ddddeeeeffff");

        assertThat(name)
                .isEqualTo("intent_ab12cd34aaaa")
                .startsWith("intent_");
    }

    @Test
    void indexNameHandlesNullAgentId() {
        assertThat(TurAnalyticsIntentIndexer.indexNameFor(null)).isEqualTo("intent_unknown");
    }

    @Test
    void indexRowPushesEnabledRowAndCommits() {
        TurAnalyticsIntent row = intent("intent-1", "Refund request",
                "refund\nmoney back", "agent-1");
        when(seInstanceRepository.findAll()).thenReturn(List.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexStandaloneDocument(eq(seInstance), anyString(), any())).thenReturn(true);

        boolean ok = indexer.indexRow(row);

        assertThat(ok).isTrue();
        ArgumentCaptor<Map<String, Object>> docCaptor = ArgumentCaptor.captor();
        verify(plugin).indexStandaloneDocument(eq(seInstance), eq("intent_agent1"),
                docCaptor.capture());
        Map<String, Object> doc = docCaptor.getValue();
        assertThat(doc)
                .containsEntry("id", "intent-1")
                .containsEntry("agentId", "agent-1")
                .containsEntry("label", "Refund request")
                .containsEntry("samples", "refund money back");
        verify(plugin).commitStandalone(seInstance, "intent_agent1");
    }

    @Test
    void disabledRowIsRemovedFromSeNotIndexed() {
        TurAnalyticsIntent row = intent("intent-1", "Refund request",
                "refund samples", "agent-1");
        row.setEnabled(0);
        when(seInstanceRepository.findAll()).thenReturn(List.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.deIndexStandalone(eq(seInstance), anyString(), eq("intent-1")))
                .thenReturn(true);

        indexer.indexRow(row);

        verify(plugin, never()).indexStandaloneDocument(any(), any(), any());
        verify(plugin).deIndexStandalone(seInstance, "intent_agent1", "intent-1");
    }

    @Test
    void noSeInstanceReturnsFalseWithoutCallingPlugin() {
        when(seInstanceRepository.findAll()).thenReturn(List.of());

        boolean ok = indexer.indexRow(intent("intent-1", "x", "y", "agent-1"));

        assertThat(ok).isFalse();
        verify(plugin, never()).indexStandaloneDocument(any(), any(), any());
    }

    @Test
    void unsupportedPluginIsSwallowed() {
        TurAnalyticsIntent row = intent("intent-1", "Refund request",
                "refund samples", "agent-1");
        when(seInstanceRepository.findAll()).thenReturn(List.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexStandaloneDocument(any(), any(), any()))
                .thenThrow(new UnsupportedOperationException("lucene plugin has no standalone"));

        boolean ok = indexer.indexRow(row);

        assertThat(ok).isFalse();
    }

    @Test
    void reindexAgentWipesSliceThenBatchPushes() {
        TurAnalyticsIntent r1 = intent("i1", "Refund", "refund please", "agent-1");
        TurAnalyticsIntent r2 = intent("i2", "Shipping", "where is my package", "agent-1");
        when(seInstanceRepository.findAll()).thenReturn(List.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(r1, r2));
        when(plugin.indexStandaloneDocuments(eq(seInstance), eq("intent_agent1"), any()))
                .thenReturn(2);

        int indexed = indexer.reindexAgent("agent-1");

        assertThat(indexed).isEqualTo(2);
        verify(plugin).deIndexStandaloneByField(seInstance, "intent_agent1", "agentId", "agent-1");
        verify(plugin, times(1)).indexStandaloneDocuments(eq(seInstance),
                eq("intent_agent1"), any());
        verify(plugin).commitStandalone(seInstance, "intent_agent1");
    }

    @Test
    void configuredSeInstanceIdWinsOverFirstInList() {
        TurSEInstance configured = mockSeInstance("configured");
        when(seInstanceRepository.findById("configured-id")).thenReturn(java.util.Optional.of(configured));

        TurAnalyticsIntentIndexer pinned = new TurAnalyticsIntentIndexer(intentRepository,
                seInstanceRepository, agentRepository, pluginFactory, "configured-id");

        assertThat(pinned.resolveSeInstance()).contains(configured);
        verify(seInstanceRepository, never()).findAll();
    }

    @Test
    void perAgentBindingWinsOverConfiguredProperty() {
        TurSEInstance agentBoundSe = mockSeInstance("agent-bound");
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setAnalyticsSeInstance(agentBoundSe);
        when(agentRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));

        TurAnalyticsIntentIndexer pinned = new TurAnalyticsIntentIndexer(intentRepository,
                seInstanceRepository, agentRepository, pluginFactory, "configured-id");

        assertThat(pinned.resolveSeInstance("agent-1")).contains(agentBoundSe);
        verify(seInstanceRepository, never()).findById("configured-id");
    }

    @Test
    void perAgentBindingFallsThroughWhenAgentHasNone() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setAnalyticsSeInstance(null);
        TurSEInstance configuredSe = mockSeInstance("configured");
        when(agentRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));
        when(seInstanceRepository.findById("configured-id")).thenReturn(java.util.Optional.of(configuredSe));

        TurAnalyticsIntentIndexer pinned = new TurAnalyticsIntentIndexer(intentRepository,
                seInstanceRepository, agentRepository, pluginFactory, "configured-id");

        assertThat(pinned.resolveSeInstance("agent-1")).contains(configuredSe);
    }

    private static TurAnalyticsIntent intent(String id, String label, String samples,
            String agentId) {
        TurAnalyticsIntent intent = new TurAnalyticsIntent();
        intent.setId(id);
        intent.setLabel(label);
        intent.setSamples(samples);
        intent.setEnabled(1);
        TurAIAgent agent = new TurAIAgent();
        agent.setId(agentId);
        intent.setTurAIAgent(agent);
        return intent;
    }

    private static TurSEInstance mockSeInstance(String label) {
        TurSEInstance instance = new TurSEInstance();
        instance.setId(label);
        instance.setTitle(label);
        return instance;
    }
}
