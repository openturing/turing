/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

class TurDelegateAgentToolCallbackTest {

    private final TurAgentChatExecutor executor = mock(TurAgentChatExecutor.class);
    private final TurAIAgentRepository agentRepository = mock(TurAIAgentRepository.class);
    private final TurLLMInstanceRepository llmRepository = mock(TurLLMInstanceRepository.class);

    private TurDelegateAgentToolCallback callback(Map<String, String> allowed) {
        return new TurDelegateAgentToolCallback(allowed, executor, agentRepository, llmRepository);
    }

    @Test
    void toolDefinitionIsStable() {
        var cb = callback(Map.of("returns", "spec1"));
        assertThat(cb.getToolDefinition().name()).isEqualTo("delegate_to_agent");
        assertThat(cb.getToolDefinition().inputSchema()).contains("agent").contains("task");
    }

    @Test
    void blankArgsAreRejectedWithoutDelegating() {
        var cb = callback(Map.of("returns", "spec1"));
        assertThat(cb.call("{}", null)).contains("'agent'");
        assertThat(cb.call("{\"agent\":\"Returns\"}", null)).contains("'task'");
        verifyNoInteractions(executor, agentRepository, llmRepository);
    }

    @Test
    void unknownSpecialistIsRejectedWithAvailableList() {
        var cb = callback(Map.of("returns", "spec1", "billing", "spec2"));
        String out = cb.call("{\"agent\":\"Legal\",\"task\":\"review\"}", null);
        assertThat(out).contains("No specialist named 'Legal'")
                .contains("Available specialists:")
                .contains("spec1")
                .contains("spec2");
        verifyNoInteractions(executor);
    }

    @Test
    void resolutionIsCaseInsensitive() {
        // An allowlisted key matched case-insensitively does NOT hit the "unknown"
        // path (it proceeds to delegation, which then uses the mocked repos).
        var cb = callback(Map.of("returns", "spec1"));
        String out = cb.call("{\"agent\":\"RETURNS\",\"task\":\"do it\"}", null);
        assertThat(out).doesNotContain("No specialist named");
    }
}
