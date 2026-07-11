/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.synonym;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.sn.synonym.TurSynonymClusterer.TurSynonymCandidate;

import lombok.extern.slf4j.Slf4j;

/**
 * T667 / §XXXIX (Block AP) — AI-assisted synonym mining, the "better than
 * Algolia" differentiator. Reads a site's most-searched query-log terms
 * ({@link TurSNSiteMetricAccessRepository#topTerms}), embeds each with the
 * default embedding model, and groups them by embedding proximity
 * ({@link TurSynonymClusterer}) into <em>candidate</em> synonym sets.
 *
 * <p>Per the grounding invariant, nothing is ever auto-applied: proposals come
 * back as disabled {@code REGULAR} {@link TurSNSynonymDto}s (id {@code null},
 * not persisted) for a human to review, edit, save and push. This turns
 * synonyms from a maintenance chore into a suggested-and-approved workflow.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSynonymMiningService {

    private static final double DEFAULT_THRESHOLD = 0.82;
    private static final int MAX_CLUSTER_SIZE = 12;

    private final TurRagContextBuilder ragContextBuilder;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;

    public TurSynonymMiningService(TurRagContextBuilder ragContextBuilder,
            TurSNSiteMetricAccessRepository metricAccessRepository) {
        this.ragContextBuilder = ragContextBuilder;
        this.metricAccessRepository = metricAccessRepository;
    }

    /**
     * Mines candidate synonym sets from the site's query log.
     *
     * @param site      the SN site whose search-term log to mine
     * @param locale    the locale the proposed rules target
     * @param limit     how many top terms to consider (clamped to ≥1)
     * @param threshold cosine similarity to group two terms (0 → default)
     * @return disabled candidate synonym sets for human review (never persisted)
     */
    public List<TurSNSynonymDto> mine(TurSNSite site, Locale locale, int limit, double threshold) {
        EmbeddingModel embeddingModel = ragContextBuilder.buildFromGlobalSettings()
                .map(TurRagContextBuilder.RagInfrastructure::embeddingModel)
                .orElse(null);
        if (embeddingModel == null) {
            // No default embedding model configured — nothing to mine against.
            log.debug("Synonym mining skipped: no default embedding model configured.");
            return List.of();
        }

        List<TurSNSiteMetricAccessTerm> topTerms = metricAccessRepository.topTerms(site,
                PageRequest.of(0, Math.max(1, limit)));
        List<TurSynonymCandidate> candidates = embedTerms(embeddingModel, topTerms);

        double effectiveThreshold = threshold > 0 ? threshold : DEFAULT_THRESHOLD;
        List<List<String>> clusters = TurSynonymClusterer.cluster(candidates, effectiveThreshold,
                MAX_CLUSTER_SIZE);
        return toProposals(clusters, locale);
    }

    private List<TurSynonymCandidate> embedTerms(EmbeddingModel embeddingModel,
            List<TurSNSiteMetricAccessTerm> topTerms) {
        List<TurSynonymCandidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TurSNSiteMetricAccessTerm entry : topTerms) {
            String term = entry.getTerm() == null ? null : entry.getTerm().trim();
            if (term == null || term.isEmpty() || !seen.add(term.toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                float[] vector = embeddingModel.embed(term);
                if (vector != null && vector.length > 0) {
                    candidates.add(new TurSynonymCandidate(term, vector));
                }
            } catch (RuntimeException e) {
                log.debug("Could not embed term '{}' for synonym mining: {}", term, e.getMessage());
            }
        }
        return candidates;
    }

    /** Maps each cluster to a disabled REGULAR proposal (pure — unit-tested). */
    static List<TurSNSynonymDto> toProposals(List<List<String>> clusters, Locale locale) {
        List<TurSNSynonymDto> proposals = new ArrayList<>();
        for (List<String> cluster : clusters) {
            proposals.add(new TurSNSynonymDto(null, "Suggested: " + cluster.get(0),
                    TurSNSynonymType.REGULAR, locale, null, cluster, false));
        }
        return proposals;
    }
}
