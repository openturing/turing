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
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurChatFlowTriggerConflictDto;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

/**
 * Pins the T91 trigger-conflict detection: shape of emitted pairs, severity
 * thresholds, analyzer isolation, and the suppression rules that keep
 * legitimate small-overlap descriptions quiet.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTriggerConflictServiceTest {

    @Mock
    private TurChatFlowRepository chatFlowRepository;

    @InjectMocks
    private TurTriggerConflictService service;

    @Test
    void emitsHighSeverityForHeavilyOverlappingPair() {
        var planA = flow("plan-a", "Plan A",
                "Ative para planejamento de carreira e desenvolvimento profissional",
                TurChatFlowTriggerLanguage.PT);
        var planB = flow("plan-b", "Plan B",
                "Ative para planejamento de carreira e desenvolvimento individual",
                TurChatFlowTriggerLanguage.PT);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(planA, planB));

        List<TurChatFlowTriggerConflictDto> conflicts = service.detect("agent-1");

        assertThat(conflicts).hasSize(1);
        TurChatFlowTriggerConflictDto conflict = conflicts.get(0);
        assertThat(conflict.severity()).isEqualTo("HIGH");
        assertThat(conflict.similarity()).isGreaterThanOrEqualTo(TurTriggerConflictService.HIGH_JACCARD);
        // Pair ordering is deterministic by id so the UI dedupes safely.
        assertThat(conflict.flowAId()).isEqualTo("plan-a");
        assertThat(conflict.flowBId()).isEqualTo("plan-b");
        assertThat(conflict.intersectionSize()).isGreaterThanOrEqualTo(TurTriggerConflictService.WARNING_INTERSECTION);
        assertThat(conflict.suggestion()).containsIgnoringCase("NÃO ATIVAR");
    }

    @Test
    void doesNotCompareFlowsAcrossDifferentAnalyzers() {
        // PT vs EN — the procedural router never compares these in the same
        // MLT pass, so the conflict resolver must not either even when the
        // two descriptions look superficially similar.
        var ptFlow = flow("pt-1", "PT",
                "planos de carreira programa desenvolvimento profissional individual",
                TurChatFlowTriggerLanguage.PT);
        var enFlow = flow("en-1", "EN",
                "career plans program professional individual development",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(ptFlow, enFlow));

        assertThat(service.detect("agent-1")).isEmpty();
    }

    @Test
    void ignoresDisabledOrBlankFlows() {
        var enabled = flow("a", "Enabled",
                "fluxo de planejamento de carreira para colaboradores experientes",
                TurChatFlowTriggerLanguage.PT);
        var disabled = flow("b", "Disabled",
                "fluxo de planejamento de carreira para colaboradores experientes",
                TurChatFlowTriggerLanguage.PT);
        disabled.setEnabled(0);
        var blank = flow("c", "Blank", "  ", TurChatFlowTriggerLanguage.PT);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(enabled, disabled, blank));

        // Only one eligible flow remains → nothing to compare.
        assertThat(service.detect("agent-1")).isEmpty();
    }

    @Test
    void suppressesPairsBelowIntersectionFloor() {
        // Two-stem descriptions share one stem — Jaccard would land around
        // 0.33 (above WARNING_JACCARD) but the intersection floor of three
        // shared stems holds it back.
        var first = flow("a", "A", "planos carreira", TurChatFlowTriggerLanguage.PT);
        var second = flow("b", "B", "planos vendas", TurChatFlowTriggerLanguage.PT);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(first, second));

        assertThat(service.detect("agent-1")).isEmpty();
    }

    @Test
    void emitsEnglishSuggestionForEnglishAnalyzerBucket() {
        var first = flow("a", "A",
                "career planning development professional individual mentor",
                TurChatFlowTriggerLanguage.EN);
        var second = flow("b", "B",
                "career planning development individual professional mentor program",
                TurChatFlowTriggerLanguage.EN);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(first, second));

        List<TurChatFlowTriggerConflictDto> conflicts = service.detect("agent-1");

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).suggestion())
                .containsIgnoringCase("Both flows share")
                .containsIgnoringCase("DO NOT ACTIVATE");
    }

    @Test
    void sortsConflictsByDescendingSimilarity() {
        // Three flows, two pairs above the threshold — the loudest one
        // should come first so the UI surfaces the worst conflict at the
        // top of the panel.
        var planA = flow("a", "A",
                "planejamento de carreira desenvolvimento individual profissional",
                TurChatFlowTriggerLanguage.PT);
        var planB = flow("b", "B",
                "planejamento de carreira desenvolvimento individual profissional adicional",
                TurChatFlowTriggerLanguage.PT);
        var planC = flow("c", "C",
                "planejamento de carreira para programa avançado executivo",
                TurChatFlowTriggerLanguage.PT);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("agent-1"))
                .thenReturn(List.of(planA, planB, planC));

        List<TurChatFlowTriggerConflictDto> conflicts = service.detect("agent-1");

        assertThat(conflicts).isNotEmpty();
        for (int i = 1; i < conflicts.size(); i++) {
            assertThat(conflicts.get(i).similarity())
                    .as("Pair %d must be no more similar than pair %d", i, i - 1)
                    .isLessThanOrEqualTo(conflicts.get(i - 1).similarity());
        }
    }

    @Test
    void emptyAgentIdReturnsEmptyWithoutQueryingRepository() {
        assertThat(service.detect(null)).isEmpty();
        assertThat(service.detect("")).isEmpty();
        assertThat(service.detect("   ")).isEmpty();
    }

    private static TurChatFlow flow(String id, String name, String triggerDescription,
            TurChatFlowTriggerLanguage language) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setName(name);
        flow.setTriggerDescription(triggerDescription);
        flow.setTriggerLanguage(language);
        flow.setEnabled(1);
        return flow;
    }
}
