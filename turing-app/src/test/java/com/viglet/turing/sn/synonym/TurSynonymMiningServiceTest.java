/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.synonym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

/**
 * Unit tests for AI-assisted synonym mining (T667) — the embed + cluster +
 * propose orchestration, over mocked embedding and query-log seams.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSynonymMiningServiceTest {

    private static final Map<String, float[]> VECTORS = Map.of(
            "tv", new float[] { 1f, 0f },
            "television", new float[] { 0.99f, 0.02f },
            "telly", new float[] { 0.98f, 0.05f },
            "laptop", new float[] { 0f, 1f },
            "notebook", new float[] { 0.02f, 0.99f });

    private static TurSNSiteMetricAccessTerm term(String t) {
        return new TurSNSiteMetricAccessTerm(t, 10L, 5d);
    }

    @Test
    void minesClustersFromQueryLogAsDisabledProposals() {
        TurRagContextBuilder ragBuilder = mock(TurRagContextBuilder.class);
        TurSNSiteMetricAccessRepository repo = mock(TurSNSiteMetricAccessRepository.class);
        EmbeddingModel embed = mock(EmbeddingModel.class);

        when(ragBuilder.buildFromGlobalSettings())
                .thenReturn(Optional.of(new RagInfrastructure(null, embed, null, null, null, null)));
        when(embed.embed(anyString())).thenAnswer(inv -> VECTORS.get(inv.getArgument(0)));
        when(repo.topTerms(any(), any())).thenReturn(List.of(
                term("tv"), term("television"), term("telly"), term("laptop"), term("notebook")));

        TurSynonymMiningService service = new TurSynonymMiningService(ragBuilder, repo);
        List<TurSNSynonymDto> proposals = service.mine(mock(TurSNSite.class), Locale.ENGLISH, 100, 0.9);

        assertEquals(2, proposals.size());
        assertTrue(proposals.stream().allMatch(p -> p.type() == TurSNSynonymType.REGULAR));
        // never auto-applied: proposals come back disabled + unpersisted (id null)
        assertTrue(proposals.stream().allMatch(p -> !p.enabled() && p.id() == null));
        assertTrue(proposals.stream().anyMatch(p -> p.terms().containsAll(List.of("tv", "television", "telly"))));
        assertTrue(proposals.stream().anyMatch(p -> p.terms().containsAll(List.of("laptop", "notebook"))));
    }

    @Test
    void returnsEmptyWhenNoDefaultEmbeddingModel() {
        TurRagContextBuilder ragBuilder = mock(TurRagContextBuilder.class);
        TurSNSiteMetricAccessRepository repo = mock(TurSNSiteMetricAccessRepository.class);
        when(ragBuilder.buildFromGlobalSettings()).thenReturn(Optional.empty());

        TurSynonymMiningService service = new TurSynonymMiningService(ragBuilder, repo);
        assertTrue(service.mine(mock(TurSNSite.class), Locale.ENGLISH, 100, 0.9).isEmpty());
    }

    @Test
    void toProposalsMarksSuggestionsDisabled() {
        List<TurSNSynonymDto> proposals = TurSynonymMiningService.toProposals(
                List.of(List.of("car", "auto")), Locale.forLanguageTag("pt-BR"));
        assertEquals(1, proposals.size());
        assertFalse(proposals.get(0).enabled());
        assertEquals(TurSNSynonymType.REGULAR, proposals.get(0).type());
        assertEquals(Locale.forLanguageTag("pt-BR"), proposals.get(0).language());
    }
}
