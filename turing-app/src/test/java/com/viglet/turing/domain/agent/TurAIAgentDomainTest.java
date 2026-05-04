/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Behaviour tests for {@link TurAIAgentDomain}. */
class TurAIAgentDomainTest {

    @Test
    void isEnabledIsTrueOnlyWhenEnabledFlagIsOne() {
        assertThat(build(1, Set.of()).isEnabled()).isTrue();
        assertThat(build(0, Set.of()).isEnabled()).isFalse();
        assertThat(build(2, Set.of()).isEnabled()).isFalse();
    }

    @Test
    void allowsLlmInstanceTrueWhenIdIsInTheSet() {
        TurAIAgentDomain agent = build(1, Set.of("llm-1", "llm-2"));

        assertThat(agent.allowsLlmInstance("llm-1")).isTrue();
        assertThat(agent.allowsLlmInstance("llm-2")).isTrue();
        assertThat(agent.allowsLlmInstance("llm-3")).isFalse();
    }

    @Test
    void allowsLlmInstanceHandlesNullSet() {
        TurAIAgentDomain agent = build(1, null);
        assertThat(agent.allowsLlmInstance("any")).isFalse();
    }

    private static TurAIAgentDomain build(int enabled, Set<String> llmIds) {
        return new TurAIAgentDomain("a", "title", null, null, "prompt", null, enabled, false, null,
                llmIds, Set.of(), Set.of(), null, null);
    }
}
