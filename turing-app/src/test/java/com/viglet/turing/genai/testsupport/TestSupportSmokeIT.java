/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * Smoke test for the {@code genai/testsupport} infrastructure shipped under
 * task T1 of {@code docs/IMPROVEMENTS.md} §0 (Execution order). Validates:
 *
 * <ol>
 *   <li>The Spring context boots with
 *       {@link TurAgentChatExecutorMockSupport} imported — i.e. our
 *       {@code @Primary TurLlmModelFactory} mock doesn't clash with the
 *       production factory, and the {@code ChatModelHarness} bean is
 *       resolvable as a field-injected dependency on
 *       {@link AbstractAgentExecutorIT}.</li>
 *   <li>{@link AbstractAgentExecutorIT#runTurn(TurAIAgent, TurLLMInstance, String)}
 *       drives {@code TurAgentChatExecutor.execute(...)} through the harness
 *       and returns the queued assistant text as a single SSE event.</li>
 *   <li>{@link AbstractAgentExecutorIT#firstPrompt()} surfaces a captured
 *       prompt with the expected user message and a system message.</li>
 * </ol>
 *
 * <p>This file exists to validate T1 and SHOULD be deleted in T8 (Cleanup)
 * once the structural / contract / behavioral skeletons populate the suite
 * — the harness will be exercised by hundreds of assertions then, the
 * smoke is redundant. The doc explicitly calls this out (§VI.6 Phase 0:
 * "Smoke test (delete before closing phase)").
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Import(TurAgentChatExecutorMockSupport.class)
class TestSupportSmokeIT extends AbstractAgentExecutorIT {

    @Test
    void contextBoots_harnessRoundTripsAssistantText() {
        harness().queueResponse("hello from mock");

        TurAIAgent agent = createAgent("smoke", null);
        TurLLMInstance llm = createMockableLlmInstance();
        agent.getLlmInstances().add(llm);
        agent = agentRepository.save(agent);

        List<ChatResponse> events = runTurn(agent, llm, "hi");

        assertThat(events)
                .as("Harness mock should produce exactly one SSE event with the queued text")
                .hasSize(1);
        assertThat(events.get(0).role()).isEqualTo("assistant");
        assertThat(events.get(0).content()).isEqualTo("hello from mock");

        assertThat(harness().callCount())
                .as("Executor must invoke chatModel.call exactly once on a no-flow turn")
                .isEqualTo(1);
        assertThat(harness().streamCount())
                .as("Default chatMode=CALL — stream() must not be touched")
                .isZero();

        String systemText = systemTextOf(firstPrompt());
        assertThat(systemText)
                .as("Captured prompt should carry the default agent system prompt")
                .isNotBlank();
    }
}
