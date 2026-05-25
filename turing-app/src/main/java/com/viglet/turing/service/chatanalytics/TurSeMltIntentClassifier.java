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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;
import com.viglet.turing.plugins.se.TurSEStandaloneHit;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * T28 / §III.5 — search-engine-backed {@code MoreLikeThis} classifier
 * strategy. Dispatches the user transcript through
 * {@link TurSearchEnginePlugin#moreLikeThisStandalone} so Solr
 * ({@code /mlt} request handler) and Elasticsearch
 * ({@code more_like_this} query) both work through the same code path
 * — whichever engine the configured {@link TurSEInstance} resolves to.
 *
 * <p>Production target for §III.5: shared across pods, persistent,
 * supports point-in-time queries. The embedded
 * {@link TurLuceneIntentClassifier} (Phase A) stays as the
 * dev/single-instance fallback when no SE is configured or the install
 * doesn't want a Solr/ES round-trip on every enricher cycle.
 *
 * <h2>Availability gating</h2>
 *
 * {@link #isAvailable(String)} requires:
 * <ol>
 *   <li>a non-blank agent id (the classifier is per-agent);</li>
 *   <li>a resolvable {@link TurSEInstance} (per the indexer's binding
 *       — see {@link TurAnalyticsIntentIndexer#resolveSeInstance()});</li>
 *   <li>at least one enabled catalog row for the agent.</li>
 * </ol>
 *
 * The orchestrator's {@code auto} cascade lands here ahead of the
 * embedded Lucene strategy when all three are satisfied — that order
 * matches the priority list in {@link TurChatIntentClassifier}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurSeMltIntentClassifier implements TurIntentClassifierStrategy {

    public static final String TYPE = TurChatIntentClassifier.SEARCH_ENGINE_MLT_TYPE;

    private static final String FIELD_SAMPLES = "samples";

    /**
     * Minimum MLT score for the top hit to be reported as a
     * classification. The SE plugins return raw Lucene BM25 scores
     * (Solr / ES under the hood) which are not pre-normalized; the
     * floor protects against incidental term overlaps on short
     * transcripts. Calibrated empirically against the
     * {@link TurLuceneIntentClassifier}'s 0.5 floor.
     */
    private static final float MIN_SCORE_FLOOR = 0.5f;

    private final TurAnalyticsIntentRepository intentRepository;
    private final TurAnalyticsIntentIndexer indexer;
    private final TurSearchEnginePluginFactory pluginFactory;

    public TurSeMltIntentClassifier(TurAnalyticsIntentRepository intentRepository,
            TurAnalyticsIntentIndexer indexer,
            TurSearchEnginePluginFactory pluginFactory) {
        this.intentRepository = intentRepository;
        this.indexer = indexer;
        this.pluginFactory = pluginFactory;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isAvailable(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        if (indexer.resolveSeInstance(agentId).isEmpty()) {
            return false;
        }
        List<TurAnalyticsIntent> catalog = intentRepository
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc(agentId, 1);
        return catalog != null && !catalog.isEmpty()
                && catalog.stream().anyMatch(i -> i.getSamples() != null && !i.getSamples().isBlank());
    }

    @Override
    public TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
            List<Map<String, Object>> messages) {
        if (agentId == null || agentId.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        String transcript = TurLlmIntentClassifier.buildTranscript(firstUserMessage, messages);
        if (transcript.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        Optional<TurSEInstance> seInstance = indexer.resolveSeInstance(agentId);
        if (seInstance.isEmpty()) {
            return TurChatSessionEnrichment.unclassified();
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(seInstance.get());
        String indexName = TurAnalyticsIntentIndexer.indexNameFor(agentId);
        List<TurSEStandaloneHit> hits;
        try {
            hits = plugin.moreLikeThisStandalone(seInstance.get(), indexName,
                    FIELD_SAMPLES, transcript, 5);
        } catch (UnsupportedOperationException e) {
            log.debug("[SeMltIntentClassifier] plugin '{}' has no MLT support — "
                    + "falling back to unclassified", plugin.getPluginType());
            return TurChatSessionEnrichment.unclassified();
        } catch (RuntimeException e) {
            log.warn("[SeMltIntentClassifier] MLT search on '{}' failed: {}",
                    indexName, e.getMessage());
            return TurChatSessionEnrichment.unclassified();
        }
        if (hits == null || hits.isEmpty()) {
            return TurChatSessionEnrichment.unclassified();
        }
        TurSEStandaloneHit top = hits.get(0);
        if (top.score() < MIN_SCORE_FLOOR) {
            return TurChatSessionEnrichment.unclassified();
        }
        String label = top.id();
        if (label == null || label.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        double sumScores = 0.0;
        for (TurSEStandaloneHit hit : hits) {
            sumScores += hit.score();
        }
        double confidence = sumScores > 0 ? top.score() / sumScores : 0.0;
        return new TurChatSessionEnrichment(
                TurChatIntentLabel.OTHER,    // free-form catalog labels stay out of the enum
                confidence,
                label,                        // catalog label rendered into goalSummary
                TurChatGoalAchieved.UNKNOWN,
                TurChatSentiment.UNKNOWN,
                Collections.emptyList(),     // SE plugins don't surface key-terms today
                Instant.now());
    }
}
