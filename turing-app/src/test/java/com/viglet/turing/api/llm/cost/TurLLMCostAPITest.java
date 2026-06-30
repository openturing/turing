/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.llm.cost.TurLLMCostAPI.AgentBudgetStatus;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

/**
 * T184 / §X.14.d — per-agent budget status (alert thresholds) for the AI-Spend
 * card: over-budget detection, exclusion of agents with no budget, and ordering.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLLMCostAPITest {

    @Mock private TurLLMTokenUsageRepository tokenUsageRepository;
    @Mock private TurAIAgentRepository agentRepository;

    @InjectMocks private TurLLMCostAPI api;

    private TurAIAgent agent(String id, String title, Double budget) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(id);
        agent.setTitle(title);
        agent.setMonthlyBudgetUsd(budget);
        return agent;
    }

    @Test
    void budgetStatusFlagsOverBudgetExcludesUnbudgetedAndSortsBySpend() {
        TurAIAgent over = agent("a-over", "Over", 100.0);
        TurAIAgent under = agent("b-under", "Under", 100.0);
        TurAIAgent noBudget = agent("c-none", "NoBudget", null);
        when(agentRepository.findAll()).thenReturn(List.of(under, over, noBudget));
        when(tokenUsageRepository.sumCostByAgentSince(eq("a-over"), any(LocalDateTime.class)))
                .thenReturn(150.0);
        when(tokenUsageRepository.sumCostByAgentSince(eq("b-under"), any(LocalDateTime.class)))
                .thenReturn(0.0);

        List<AgentBudgetStatus> status = api.budgetStatus();

        assertThat(status).hasSize(2); // c-none excluded (no budget)
        // sorted by month-to-date spend desc → over first
        AgentBudgetStatus first = status.get(0);
        assertThat(first.agentId()).isEqualTo("a-over");
        assertThat(first.monthToDateSpendUsd()).isEqualTo(150.0);
        assertThat(first.overBudget()).isTrue();
        assertThat(first.projectedOverBudget()).isTrue();
        assertThat(first.projectedMonthEndUsd()).isGreaterThanOrEqualTo(150.0);

        AgentBudgetStatus second = status.get(1);
        assertThat(second.agentId()).isEqualTo("b-under");
        assertThat(second.overBudget()).isFalse();
        assertThat(second.projectedOverBudget()).isFalse();
    }
}
