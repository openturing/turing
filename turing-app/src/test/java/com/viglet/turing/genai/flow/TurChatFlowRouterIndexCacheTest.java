/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

/**
 * T27 / §II.2.3 — pins the per-agent {@code SearcherManager} cache wired
 * into {@link TurChatFlowEngineService#tryProceduralRoute(String, java.util.List, String)}.
 *
 * <p>Three contracts to lock down:
 * <ul>
 *   <li>repeated calls for the same agent rebuild the Lucene index <em>once</em></li>
 *   <li>{@link TurChatFlowEngineService#evictRouterIndexes()} forces a rebuild on the next call</li>
 *   <li>candidates excluded by the per-conversation eligibility mask never win,
 *       even when they would have been the top-scoring document in the bucket</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatFlowRouterIndexCacheTest {

    @Mock
    private TurChatFlowStateRepository stateRepository;
    @Mock
    private TurChatFlowRepository chatFlowRepository;
    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;
    @Mock
    private TurChatAnalyticsService chatAnalyticsService;
    @Mock
    private TurChatSlotEventBus slotEventBus;
    @Mock
    private CacheManager cacheManager;

    private TurChatFlowEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new TurChatFlowEngineService(stateRepository, chatFlowRepository,
                submissionRepository, chatAnalyticsService, slotEventBus,
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatslots.TurChatSlotAuditService.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatanalytics.TurAbBanditService.class),
                cacheManager,
                org.mockito.Mockito.mock(TurFunctionCallNodeExecutor.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.genai.flow.routine.TurScheduleAgentNodeExecutor.class),
                org.mockito.Mockito.mock(TurChatWebhookNodeExecutor.class),
                List.<TurChatFlowGuardrailStrategy>of());
    }

    @Test
    void cachedIndexIsBuiltOncePerAgent() {
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(refund, shipping));

        var first = engine.tryProceduralRoute("agent-1", candidates(refund, shipping), "refund");
        var second = engine.tryProceduralRoute("agent-1", candidates(refund, shipping), "refund");

        assertThat(first).contains("refund");
        assertThat(second).contains("refund");
        assertThat(engine.routerIndexCacheSize()).isEqualTo(1);
        verify(chatFlowRepository, times(1)).findByTurAIAgent_IdOrderByNameAsc("agent-1");
    }

    @Test
    void evictForcesRebuildOnNextCall() {
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(refund, shipping));

        engine.tryProceduralRoute("agent-1", candidates(refund, shipping), "refund");
        assertThat(engine.routerIndexCacheSize()).isEqualTo(1);

        engine.evictRouterIndexes();
        assertThat(engine.routerIndexCacheSize()).isZero();

        engine.tryProceduralRoute("agent-1", candidates(refund, shipping), "refund");
        assertThat(engine.routerIndexCacheSize()).isEqualTo(1);
        verify(chatFlowRepository, times(2)).findByTurAIAgent_IdOrderByNameAsc("agent-1");
    }

    @Test
    void eligibilityMaskExcludesCompletedFlows() {
        // Index built with all 3 flows; per-conversation candidates exclude
        // "refund" (ONCE + already completed). The router must pick from the
        // remaining 2 — never "refund", even though it would have scored top.
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);
        var account = flow("account", "account password reset login",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(refund, shipping, account));

        var picked = engine.tryProceduralRoute("agent-1",
                candidates(shipping, account), "refund chargeback");

        // No eligible candidate scored — must NOT leak the excluded "refund"
        // even though it was the top MLT hit in the cached bucket.
        assertThat(picked).isNotPresent();
    }

    @Test
    void emptyCandidatesShortCircuitsWithoutBuildingIndex() {
        var picked = engine.tryProceduralRoute("agent-1", List.of(), "any message");

        assertThat(picked).isEmpty();
        assertThat(engine.routerIndexCacheSize()).isZero();
        verify(chatFlowRepository, never()).findByTurAIAgent_IdOrderByNameAsc("agent-1");
    }

    @Test
    void blankAgentIdFallsBackToStaticPath() {
        // Null/blank agent id (e.g. ad-hoc test harnesses) must still route
        // via the uncached T26 static method. No cache mutation, no repo call.
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);

        var picked = engine.tryProceduralRoute(null, candidates(refund, shipping), "refund");

        assertThat(picked).contains("refund");
        assertThat(engine.routerIndexCacheSize()).isZero();
        verify(chatFlowRepository, never()).findByTurAIAgent_IdOrderByNameAsc(null);
    }

    @Test
    void explainedSurfacesPerCandidateScoresForDecisivePick() {
        // T89 — proceduralRouteExplained must carry the full keyword-score map
        // and the best/second/ratio numbers, not just the winning id.
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(refund, shipping));

        var outcome = engine.proceduralRouteExplained("agent-1",
                candidates(refund, shipping), "refund reimbursement chargeback");

        assertThat(outcome).isPresent();
        var o = outcome.get();
        assertThat(o.decided()).isTrue();
        assertThat(o.pickedId()).isEqualTo("refund");
        assertThat(o.scores()).containsKey("refund");
        assertThat(o.scores().get("refund")).isGreaterThan(0f);
        assertThat(o.bestScore()).isGreaterThan(0f);
        assertThat(o.dominanceRatio()).isGreaterThan(0f);
    }

    @Test
    void explainedEmptyWhenFewerThanTwoCandidates() {
        var lone = flow("lone", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        assertThat(engine.proceduralRouteExplained("agent-1", candidates(lone), "refund"))
                .isEmpty();
    }

    @Test
    void explainedBlankAgentWrapsStaticPickWithEmptyScores() {
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);

        var outcome = engine.proceduralRouteExplained(null,
                candidates(refund, shipping), "refund");

        assertThat(outcome).isPresent();
        assertThat(outcome.get().decided()).isTrue();
        assertThat(outcome.get().pickedId()).isEqualTo("refund");
        // Static fallback exposes only the pick — no per-candidate scores.
        assertThat(outcome.get().scores()).isEmpty();
        assertThat(engine.routerIndexCacheSize()).isZero();
    }

    private static TurChatFlow flow(String id, String triggerDescription,
            TurChatFlowTriggerLanguage language) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setEnabled(1);
        flow.setTriggerDescription(triggerDescription);
        flow.setTriggerLanguage(language);
        return flow;
    }

    private static List<TurChatFlowEngineService.RouterCandidate> candidates(TurChatFlow... flows) {
        return java.util.Arrays.stream(flows)
                .map(flow -> new TurChatFlowEngineService.RouterCandidate(flow, null))
                .toList();
    }
}
