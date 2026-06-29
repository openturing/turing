/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.a — verifies the agent → instance precedence and opt-in default
 * of {@link TurPredictedOutputsResolver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPredictedOutputsResolverTest {

    private TurPredictedOutputsResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TurPredictedOutputsResolver(new TurProviderOptionsParser());
    }

    @Test
    void offByDefault_whenNothingConfigured() {
        assertThat(resolver.enabledFor(new TurAIAgent(), new TurLLMInstance())).isFalse();
        assertThat(resolver.enabledFor(null, null)).isFalse();
    }

    @Test
    void agentOption_winsOverInstance() {
        TurAIAgent agent = new TurAIAgent();
        agent.setRequestOptionsJson("{\"predicted-outputs\":\"true\"}");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"predictedOutputs\":\"false\"}");

        assertThat(resolver.enabledFor(agent, instance)).isTrue();
    }

    @Test
    void agentOptionFalse_winsOverInstanceTrue() {
        TurAIAgent agent = new TurAIAgent();
        agent.setRequestOptionsJson("{\"predicted-outputs\":\"false\"}");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"predictedOutputs\":\"true\"}");

        assertThat(resolver.enabledFor(agent, instance)).isFalse();
    }

    @Test
    void instanceOption_appliesWhenNoAgentOption() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"predictedOutputs\":\"true\"}");

        assertThat(resolver.enabledFor(new TurAIAgent(), instance)).isTrue();
    }
}
