/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurTokenCountingService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

/**
 * F.7 / §X.8.g — verifies the pre-flight cost quote: disabled without a cap, and
 * the over/under-cap decision.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAgentPreflightCostAPITest {

    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurTokenCountingService tokenCountingService;
    @Mock private TurLLMPriceService priceService;

    @InjectMocks private TurAgentPreflightCostAPI api;

    private TurAIAgent agentWithCap(Double cap) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("a1");
        agent.setPerTurnSoftCapUsd(cap);
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("openai");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("i1");
        instance.setEnabled(1);
        instance.setModelName("gpt-4o");
        instance.setTurLLMVendor(vendor);
        agent.setLlmInstances(Set.of(instance));
        return agent;
    }

    @Test
    void disabled_whenNoSoftCap() {
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agentWithCap(null)));
        var quote = api.quote("a1", new TurAgentPreflightCostAPI.PreflightRequest("hi")).getBody();
        assertThat(quote).isNotNull();
        assertThat(quote.enabled()).isFalse();
    }

    @Test
    void overSoftCap_whenProjectedCostExceedsCap() {
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agentWithCap(0.01)));
        when(tokenCountingService.isExactAvailable(any())).thenReturn(false);
        when(tokenCountingService.count(any(), any())).thenReturn(2000);
        when(priceService.computeCost(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(0.05);

        var quote = api.quote("a1", new TurAgentPreflightCostAPI.PreflightRequest("hi")).getBody();
        assertThat(quote).isNotNull();
        assertThat(quote.enabled()).isTrue();
        assertThat(quote.overSoftCap()).isTrue();
        assertThat(quote.estimatedCostUsd()).isEqualTo(0.05);
        assertThat(quote.inputTokens()).isEqualTo(2000);
    }

    @Test
    void underSoftCap_whenProjectedCostFits() {
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agentWithCap(1.00)));
        when(tokenCountingService.isExactAvailable(any())).thenReturn(false);
        when(tokenCountingService.count(any(), any())).thenReturn(100);
        when(priceService.computeCost(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(0.002);

        var quote = api.quote("a1", new TurAgentPreflightCostAPI.PreflightRequest("hi")).getBody();
        assertThat(quote).isNotNull();
        assertThat(quote.enabled()).isTrue();
        assertThat(quote.overSoftCap()).isFalse();
    }
}
