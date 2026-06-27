/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.servicetier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * F.7 / §X.8.e — verifies tier mapping + the agent → instance → global default
 * precedence of {@link TurServiceTierResolver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurServiceTierResolverTest {

    private TurConfigProperties config;
    private TurServiceTierResolver resolver;

    @BeforeEach
    void setUp() {
        config = new TurConfigProperties();
        resolver = new TurServiceTierResolver(new TurProviderOptionsParser(), config);
    }

    @Test
    void fromOption_andVendorMapping() {
        assertThat(TurServiceTier.fromOption("priority")).contains(TurServiceTier.PRIORITY);
        assertThat(TurServiceTier.fromOption("FLEX")).contains(TurServiceTier.FLEX);
        assertThat(TurServiceTier.fromOption("nonsense")).isEmpty();
        assertThat(TurServiceTier.PRIORITY.openAiValue()).isEqualTo("priority");
        assertThat(TurServiceTier.PRIORITY.anthropicValue()).isEqualTo("auto");
        assertThat(TurServiceTier.FLEX.anthropicValue()).isEqualTo("standard_only");
    }

    @Test
    void agentOption_winsOverInstanceAndGlobal() {
        config.getGenai().setServiceTier("flex");
        TurAIAgent agent = new TurAIAgent();
        agent.setRequestOptionsJson("{\"service-tier\":\"priority\"}");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"serviceTier\":\"default\"}");

        assertThat(resolver.resolveForChat(agent, instance)).contains(TurServiceTier.PRIORITY);
    }

    @Test
    void instanceOption_winsOverGlobal_whenNoAgentOption() {
        config.getGenai().setServiceTier("flex");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"serviceTier\":\"default\"}");

        assertThat(resolver.resolveForChat(new TurAIAgent(), instance))
                .contains(TurServiceTier.DEFAULT);
    }

    @Test
    void globalDefault_appliesWhenNothingElseSet() {
        config.getGenai().setServiceTier("flex");
        assertThat(resolver.resolveForChat(new TurAIAgent(), new TurLLMInstance()))
                .contains(TurServiceTier.FLEX);
    }

    @Test
    void empty_whenNothingConfigured() {
        assertThat(resolver.resolveForChat(new TurAIAgent(), new TurLLMInstance()))
                .isEqualTo(Optional.empty());
        assertThat(resolver.resolveForChat(null, null)).isEqualTo(Optional.empty());
    }
}
