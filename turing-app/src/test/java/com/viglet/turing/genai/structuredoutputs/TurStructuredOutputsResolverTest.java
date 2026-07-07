/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.structuredoutputs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.c–d — agent → instance precedence and opt-in default of the
 * cross-provider {@link TurStructuredOutputsResolver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurStructuredOutputsResolverTest {

    private TurStructuredOutputsResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TurStructuredOutputsResolver(new TurProviderOptionsParser());
    }

    @Test
    void offByDefault() {
        assertThat(resolver.enabledFor(new TurAIAgent(), new TurLLMInstance())).isFalse();
        assertThat(resolver.enabledFor(null, null)).isFalse();
    }

    @Test
    void agentOptionWins() {
        TurAIAgent agent = new TurAIAgent();
        agent.setRequestOptionsJson("{\"structured-outputs\":\"true\"}");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"structuredOutputs\":\"false\"}");
        assertThat(resolver.enabledFor(agent, instance)).isTrue();
    }

    @Test
    void instanceOptionAppliesWithoutAgentOption() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setProviderOptionsJson("{\"structuredOutputs\":\"true\"}");
        assertThat(resolver.enabledFor(new TurAIAgent(), instance)).isTrue();
    }
}
