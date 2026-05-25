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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;
import com.viglet.turing.plugins.se.TurSEStandaloneHit;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * T28 / §III.5 Phase B — pins the SE-backed MLT classifier strategy.
 *
 * <p>Plugin is mocked; tests do NOT spin up Solr or ES. The contracts
 * verified are the strategy's wiring + the {@code moreLikeThisStandalone}
 * dispatch path:
 * <ul>
 *   <li>availability: needs agent id + SE binding + non-empty catalog;</li>
 *   <li>classification: top hit's id is rendered into
 *       {@code goalSummary} as the catalog label;</li>
 *   <li>below-floor scores return unclassified;</li>
 *   <li>plugin's {@code UnsupportedOperationException} is swallowed
 *       (no SE = degrade gracefully).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSeMltIntentClassifierTest {

    @Mock
    private TurAnalyticsIntentRepository intentRepository;
    @Mock
    private TurAnalyticsIntentIndexer indexer;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;
    @Mock
    private TurSEInstance seInstance;

    private TurSeMltIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new TurSeMltIntentClassifier(intentRepository, indexer, pluginFactory);
    }

    @Test
    void unavailableWithoutAgentId() {
        assertThat(classifier.isAvailable(null)).isFalse();
        assertThat(classifier.isAvailable("")).isFalse();
    }

    @Test
    void unavailableWithoutSeInstance() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.empty());

        assertThat(classifier.isAvailable("agent-1")).isFalse();
    }

    @Test
    void unavailableWithoutCatalog() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.of(seInstance));
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of());

        assertThat(classifier.isAvailable("agent-1")).isFalse();
    }

    @Test
    void availableWithSeBindingAndCatalog() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.of(seInstance));
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(intent("Refund request", "refund samples")));

        assertThat(classifier.isAvailable("agent-1")).isTrue();
    }

    @Test
    void classifiesTopMltHitIntoGoalSummary() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.moreLikeThisStandalone(eq(seInstance), any(), eq("samples"), any(), anyInt()))
                .thenReturn(List.of(
                        new TurSEStandaloneHit("Refund request", "", Map.of(), 2.5),
                        new TurSEStandaloneHit("Shipping question", "", Map.of(), 0.8)));

        var enrichment = classifier.classify("agent-1", "refund please", List.<Map<String, Object>>of());

        assertThat(enrichment.goalSummary()).isEqualTo("Refund request");
        assertThat(enrichment.intentConfidence()).isBetween(0.0, 1.0);
        assertThat(enrichment.intentConfidence()).isGreaterThan(0.5);
    }

    @Test
    void belowFloorTopScoreReturnsUnclassified() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.moreLikeThisStandalone(eq(seInstance), any(), eq("samples"), any(), anyInt()))
                .thenReturn(List.of(new TurSEStandaloneHit("Refund request", "", Map.of(), 0.2)));

        var enrichment = classifier.classify("agent-1", "weak signal", List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
    }

    @Test
    void unsupportedPluginDegradesToUnclassified() {
        when(indexer.resolveSeInstance("agent-1")).thenReturn(Optional.of(seInstance));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.moreLikeThisStandalone(eq(seInstance), any(), eq("samples"), any(), anyInt()))
                .thenThrow(new UnsupportedOperationException("not supported by lucene"));

        var enrichment = classifier.classify("agent-1", "refund please", List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
    }

    @Test
    void blankAgentIdShortCircuits() {
        var enrichment = classifier.classify(null, "any", List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
        verify(plugin, never()).moreLikeThisStandalone(any(), any(), any(), any(), anyInt());
    }

    @Test
    void blankTranscriptShortCircuits() {
        var enrichment = classifier.classify("agent-1", null, List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
        verify(plugin, never()).moreLikeThisStandalone(any(), any(), any(), any(), anyInt());
    }

    private static TurAnalyticsIntent intent(String label, String samples) {
        TurAnalyticsIntent intent = new TurAnalyticsIntent();
        intent.setLabel(label);
        intent.setSamples(samples);
        intent.setEnabled(1);
        return intent;
    }
}
