/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurCatalogCopilotProperty;

/**
 * T818 / §LIX.1 (Block BK) — strategy resolution precedence: site pin → deployment
 * property → {@code DETERMINISTIC}. The default path must keep every existing site on
 * today's behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCopilotQueryPlannerFactoryTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    private final TurCatalogCopilotProperty property = new TurCatalogCopilotProperty();

    private final TurCopilotQueryPlanner deterministic =
            stubPlanner(TurCopilotPlanningStrategy.DETERMINISTIC);
    private final TurCopilotQueryPlanner llmAssisted =
            stubPlanner(TurCopilotPlanningStrategy.LLM_ASSISTED);
    private final TurCopilotQueryPlanner hybrid = stubPlanner(TurCopilotPlanningStrategy.HYBRID);

    private TurCopilotQueryPlannerFactory factory(TurCopilotQueryPlanner... planners) {
        return new TurCopilotQueryPlannerFactory(turSNSiteRepository, property, List.of(planners));
    }

    @Test
    void defaultsToDeterministicWhenTheSiteHasNoGenAiBinding() {
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog")).thenReturn(Optional.empty());

        var resolution = factory(deterministic, llmAssisted, hybrid).resolve("catalog");

        assertThat(resolution.planner()).isSameAs(deterministic);
        assertThat(resolution.maxPasses()).isEqualTo(2);
    }

    @Test
    void defaultsToDeterministicWhenTheSitePinsNothing() {
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog"))
                .thenReturn(Optional.of(new TurSNSiteGenAi()));

        assertThat(factory(deterministic, llmAssisted, hybrid).resolve("catalog").planner())
                .isSameAs(deterministic);
    }

    @Test
    void honoursTheSitePinnedStrategyAndDepth() {
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setCopilotPlanningStrategy(TurCopilotPlanningStrategy.LLM_ASSISTED);
        genAi.setCopilotPlanningMaxPasses(1);
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog")).thenReturn(Optional.of(genAi));

        var resolution = factory(deterministic, llmAssisted, hybrid).resolve("catalog");

        assertThat(resolution.planner()).isSameAs(llmAssisted);
        assertThat(resolution.maxPasses()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheDeploymentDefaultWhenTheSitePinsNothing() {
        property.getPlanning().setStrategy(TurCopilotPlanningStrategy.HYBRID);
        property.getPlanning().setMaxPasses(1);
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog"))
                .thenReturn(Optional.of(new TurSNSiteGenAi()));

        var resolution = factory(deterministic, llmAssisted, hybrid).resolve("catalog");

        assertThat(resolution.planner()).isSameAs(hybrid);
        assertThat(resolution.maxPasses()).isEqualTo(1);
    }

    @Test
    void letsTheSiteOverrideTheDeploymentDefault() {
        property.getPlanning().setStrategy(TurCopilotPlanningStrategy.HYBRID);
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setCopilotPlanningStrategy(TurCopilotPlanningStrategy.DETERMINISTIC);
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog")).thenReturn(Optional.of(genAi));

        assertThat(factory(deterministic, llmAssisted, hybrid).resolve("catalog").planner())
                .isSameAs(deterministic);
    }

    @Test
    void degradesToDeterministicWhenNoPlannerIsRegisteredForTheStrategy() {
        // Defensive: a strategy value with no bean must never blow up a copilot turn.
        property.getPlanning().setStrategy(TurCopilotPlanningStrategy.LLM_ASSISTED);
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog")).thenReturn(Optional.empty());

        assertThat(factory(deterministic).resolve("catalog").planner()).isSameAs(deterministic);
    }

    @Test
    void degradesToTheGlobalDefaultWhenTheLookupThrows() {
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog"))
                .thenThrow(new IllegalStateException("no session"));

        assertThat(factory(deterministic, llmAssisted, hybrid).resolve("catalog").planner())
                .isSameAs(deterministic);
    }

    @Test
    void clampsANegativeConfiguredDepthToZero() {
        property.getPlanning().setMaxPasses(-5);
        when(turSNSiteRepository.findGenAiByNameIgnoreCase("catalog")).thenReturn(Optional.empty());

        assertThat(factory(deterministic).resolve("catalog").maxPasses()).isZero();
    }

    /**
     * A real (not mocked) planner that only reports its strategy — the factory never
     * calls anything else, and a hand-rolled stub keeps these tests out of Mockito's
     * strict-stubbing bookkeeping (several tests deliberately register only one planner).
     */
    private static TurCopilotQueryPlanner stubPlanner(TurCopilotPlanningStrategy strategy) {
        return new TurCopilotQueryPlanner() {
            @Override
            public TurCopilotPlanningStrategy strategy() {
                return strategy;
            }

            @Override
            public TurCopilotQueryPlan plan(PlanRequest request) {
                return TurCopilotQueryPlan.of(TurCopilotQueryBodies.matchAll(DEFAULT_TOP_K),
                        strategy, "stub");
            }
        };
    }
}
