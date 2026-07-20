/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.properties.TurAbuseControlProperty;

/**
 * T291 / §XVI.3 — unit tests for the turn-time soft budget gate.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurChatCostBudgetGateTest {

    @Mock
    private TurLLMTokenUsageRepository tokenUsageRepository;

    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;

    private TurChatCostBudgetGate gate;
    private TurAbuseControlProperty abuseControlProperty;
    private TurAIAgent agent;
    private TurLLMInstance primary;

    @BeforeEach
    void setUp() {
        abuseControlProperty = new TurAbuseControlProperty();
        gate = new TurChatCostBudgetGate(tokenUsageRepository, llmInstanceRepository, abuseControlProperty);
        agent = new TurAIAgent();
        agent.setId("agent-1");
        primary = instance("primary", "Primary LLM");
    }

    private static TurLLMInstance instance(String id, String title) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setTitle(title);
        return i;
    }

    private void stubMonthToDateSpend(double spent) {
        when(tokenUsageRepository.sumCostByAgentSince(eq("agent-1"), any(LocalDateTime.class)))
                .thenReturn(spent);
    }

    @Test
    void shouldReturnCurrentWhenGateDisabled() {
        agent.setMonthlyBudgetUsd(null);

        assertThat(gate.resolveInstance(agent, primary)).isSameAs(primary);
        verify(tokenUsageRepository, never()).sumCostByAgentSince(anyString(), any());
    }

    @Test
    void shouldReturnCurrentWhenUnderBudget() {
        agent.setMonthlyBudgetUsd(10.0);
        stubMonthToDateSpend(4.0);

        assertThat(gate.resolveInstance(agent, primary)).isSameAs(primary);
    }

    @Test
    void shouldDowngradeWhenOverBudgetAndDowngradeConfigured() {
        agent.setMonthlyBudgetUsd(10.0);
        agent.setBudgetDowngradeLlmId("cheap");
        TurLLMInstance cheap = instance("cheap", "Cheap LLM");
        stubMonthToDateSpend(12.5);
        when(llmInstanceRepository.findById("cheap")).thenReturn(Optional.of(cheap));

        assertThat(gate.resolveInstance(agent, primary)).isSameAs(cheap);
    }

    @Test
    void shouldWarnOnlyWhenOverBudgetWithoutDowngrade() {
        agent.setMonthlyBudgetUsd(10.0);
        agent.setBudgetDowngradeLlmId(null);
        stubMonthToDateSpend(20.0);

        assertThat(gate.resolveInstance(agent, primary)).isSameAs(primary);
        verify(llmInstanceRepository, never()).findById(anyString());
    }

    @Test
    void shouldWarnOnlyWhenDowngradeInstanceNotFound() {
        agent.setMonthlyBudgetUsd(10.0);
        agent.setBudgetDowngradeLlmId("missing");
        stubMonthToDateSpend(20.0);
        when(llmInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(gate.resolveInstance(agent, primary)).isSameAs(primary);
    }

    @Test
    void warnIfTurnOverCapShouldNeverThrow() {
        agent.setPerTurnSoftCapUsd(0.01);
        assertThatCode(() -> gate.warnIfTurnOverCap(agent, 0.05)).doesNotThrowAnyException();
        assertThatCode(() -> gate.warnIfTurnOverCap(agent, 0.001)).doesNotThrowAnyException();
        assertThatCode(() -> gate.warnIfTurnOverCap(null, 1.0)).doesNotThrowAnyException();
    }

    // ---- T641 / §XXXVII.3 hard anonymous-chat cost kill-switch --------------

    @Test
    void hardCapOffByDefault() {
        assertThat(gate.isAnonymousChatHardCapExceeded()).isFalse();
        verify(tokenUsageRepository, never()).sumCostSince(any());
    }

    @Test
    void hardCapNotExceededWhenUnderCap() {
        abuseControlProperty.getChat().setHardMonthlyCapUsd(100.0);
        when(tokenUsageRepository.sumCostSince(any(LocalDateTime.class))).thenReturn(42.0);

        assertThat(gate.isAnonymousChatHardCapExceeded()).isFalse();
    }

    @Test
    void hardCapExceededWhenAtOrOverCap() {
        abuseControlProperty.getChat().setHardMonthlyCapUsd(50.0);
        when(tokenUsageRepository.sumCostSince(any(LocalDateTime.class))).thenReturn(50.0);

        assertThat(gate.isAnonymousChatHardCapExceeded()).isTrue();
    }

    @Test
    void hardCapFailsOpenOnMeteringError() {
        abuseControlProperty.getChat().setHardMonthlyCapUsd(50.0);
        when(tokenUsageRepository.sumCostSince(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(gate.isAnonymousChatHardCapExceeded()).isFalse();
    }

    // ---- T742 / §XLIX per-key gateway budget + hard cap ---------------------

    private com.viglet.turing.persistence.model.gateway.TurGatewayKey key() {
        var k = new com.viglet.turing.persistence.model.gateway.TurGatewayKey();
        k.setId("key-1");
        return k;
    }

    @Test
    void keyHardCapOffByDefault() {
        assertThat(gate.isKeyHardCapExceeded(key())).isFalse();
        verify(tokenUsageRepository, never()).sumCostByKeySince(anyString(), any());
    }

    @Test
    void keyHardCapExceededAtOrOverCap() {
        var k = key();
        k.setHardMonthlyCapUsd(30.0);
        when(tokenUsageRepository.sumCostByKeySince(eq("key-1"), any(LocalDateTime.class))).thenReturn(30.0);

        assertThat(gate.isKeyHardCapExceeded(k)).isTrue();
    }

    @Test
    void keyResolveDowngradesWhenOverSoftBudget() {
        var k = key();
        k.setMonthlyBudgetUsd(10.0);
        k.setBudgetDowngradeLlmId("cheap");
        TurLLMInstance cheap = instance("cheap", "Cheap LLM");
        when(tokenUsageRepository.sumCostByKeySince(eq("key-1"), any(LocalDateTime.class))).thenReturn(12.0);
        when(llmInstanceRepository.findById("cheap")).thenReturn(Optional.of(cheap));

        assertThat(gate.resolveInstanceForKey(k, primary)).isSameAs(cheap);
    }

    @Test
    void keyResolveKeepsCurrentUnderBudget() {
        var k = key();
        k.setMonthlyBudgetUsd(10.0);
        when(tokenUsageRepository.sumCostByKeySince(eq("key-1"), any(LocalDateTime.class))).thenReturn(3.0);

        assertThat(gate.resolveInstanceForKey(k, primary)).isSameAs(primary);
    }

    @Test
    void keyResolveKeepsCurrentWhenNoKey() {
        assertThat(gate.resolveInstanceForKey(null, primary)).isSameAs(primary);
    }
}
