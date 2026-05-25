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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;

/**
 * T28 / §III.5 — pins the embedded Lucene MLT intent classifier.
 *
 * <p>Three contracts to lock down:
 * <ul>
 *   <li>real catalog match: a transcript that overlaps an intent's
 *       samples gets labelled with that intent's label (the catalog
 *       label is rendered into {@code goalSummary}; intent enum stays
 *       at {@code OTHER} since the catalog is free-form);</li>
 *   <li>per-agent caching: same agent classified twice rebuilds the
 *       Lucene index exactly once (hot-path contract identical to T27);</li>
 *   <li>{@code evictAll()} forces a rebuild on the next call so the
 *       JPA entity listener's cache-bust is observable.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneIntentClassifierTest {

    @Mock
    private TurAnalyticsIntentRepository intentRepository;

    private TurLuceneIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new TurLuceneIntentClassifier(intentRepository);
    }

    @Test
    void classifiesTranscriptAgainstClosestCatalogLabel() {
        TurAnalyticsIntent refund = intent("Refund request",
                "I want my money back\nrefund please\ncan I get a chargeback");
        TurAnalyticsIntent shipping = intent("Shipping question",
                "where is my package\ntracking number\ndelivery delay");
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(refund, shipping));

        var enrichment = classifier.classify("agent-1", "I need my money back today, please refund me",
                List.<Map<String, Object>>of());

        assertThat(enrichment.goalSummary()).isEqualTo("Refund request");
        assertThat(enrichment.intentConfidence()).isBetween(0.0, 1.0);
    }

    @Test
    void cachedIndexBuiltOncePerAgent() {
        TurAnalyticsIntent refund = intent("Refund request",
                "refund money back chargeback billing");
        TurAnalyticsIntent shipping = intent("Shipping question",
                "shipping package tracking delivery courier");
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(refund, shipping));

        classifier.classify("agent-1", "refund chargeback billing money back",
                List.<Map<String, Object>>of());
        classifier.classify("agent-1", "shipping package tracking",
                List.<Map<String, Object>>of());

        assertThat(classifier.cacheSize()).isEqualTo(1);
        verify(intentRepository, times(1))
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1);
    }

    @Test
    void evictAllForcesRebuildOnNextClassify() {
        TurAnalyticsIntent refund = intent("Refund request",
                "refund money back chargeback billing");
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(refund));

        classifier.classify("agent-1", "refund billing chargeback",
                List.<Map<String, Object>>of());
        assertThat(classifier.cacheSize()).isEqualTo(1);

        classifier.evictAll();
        assertThat(classifier.cacheSize()).isZero();

        classifier.classify("agent-1", "refund billing chargeback",
                List.<Map<String, Object>>of());
        verify(intentRepository, times(2))
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1);
    }

    @Test
    void emptyCatalogReturnsUnclassified() {
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of());

        var enrichment = classifier.classify("agent-1", "any message", List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
    }

    @Test
    void nullAgentIdReturnsUnclassified() {
        var enrichment = classifier.classify(null, "any message", List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
        assertThat(classifier.cacheSize()).isZero();
    }

    @Test
    void belowFloorScoreReturnsUnclassified() {
        // Catalog full of unrelated terms — no transcript word overlap
        // should clear the MIN_SCORE_FLOOR.
        TurAnalyticsIntent intent = intent("Cooking recipes",
                "ingredient recipe pasta tomato basil garlic kitchen oven");
        when(intentRepository.findByTurAIAgent_IdAndEnabledOrderByLabelAsc("agent-1", 1))
                .thenReturn(List.of(intent));

        var enrichment = classifier.classify("agent-1",
                "completely different topic about taxes and accounting",
                List.<Map<String, Object>>of());

        assertThat(enrichment.intentLabel()).isEqualTo(TurChatIntentLabel.UNCLASSIFIED);
    }

    private static TurAnalyticsIntent intent(String label, String samples) {
        TurAnalyticsIntent intent = new TurAnalyticsIntent();
        intent.setLabel(label);
        intent.setSamples(samples);
        intent.setEnabled(1);
        return intent;
    }
}
