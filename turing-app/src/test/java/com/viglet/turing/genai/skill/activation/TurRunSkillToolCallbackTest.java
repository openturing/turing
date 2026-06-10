/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T323 — verifies the parent-facing {@code run_skill} delegation tool resolves
 * the named skill from its offered set, reads the agent/conversation scope from
 * the tool context, and routes to {@link TurSkillRunnerService#runSkill}, with
 * argument/unknown-skill errors returned as text.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurRunSkillToolCallbackTest {

    @Mock
    TurSkillRunnerService runnerService;

    private TurSkill alpha;
    private TurRunSkillToolCallback callback;

    @BeforeEach
    void setUp() {
        alpha = new TurSkill();
        alpha.setId("id-alpha");
        alpha.setName("alpha");
        callback = new TurRunSkillToolCallback(List.of(alpha), runnerService);
    }

    private ToolContext ctx() {
        return new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agentA",
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, "conv1"));
    }

    @Test
    void hasExpectedDefinition() {
        assertThat(callback.getToolDefinition().name()).isEqualTo("run_skill");
        assertThat(callback.getToolDefinition().inputSchema()).contains("task");
    }

    @Test
    void delegatesToRunnerWithScopedContext() {
        when(runnerService.runSkill(eq(alpha), eq("make a chart"), eq("agentA"), eq("conv1")))
                .thenReturn("done: chart.png");

        String out = callback.call(
                "{\"skill\":\"alpha\",\"task\":\"make a chart\"}", ctx());

        assertThat(out).isEqualTo("done: chart.png");
        verify(runnerService).runSkill(alpha, "make a chart", "agentA", "conv1");
    }

    @Test
    void missingSkillArgReturnsError() {
        assertThat(callback.call("{\"task\":\"do it\"}", ctx()))
                .contains("requires a non-blank 'skill'");
        verifyNoInteractions(runnerService);
    }

    @Test
    void unknownSkillReturnsErrorWithAvailableNames() {
        assertThat(callback.call("{\"skill\":\"ghost\",\"task\":\"do it\"}", ctx()))
                .contains("No skill named 'ghost'")
                .contains("alpha");
        verifyNoInteractions(runnerService);
    }

    @Test
    void nullContextStillDelegatesWithNullScope() {
        when(runnerService.runSkill(eq(alpha), eq("t"), eq(null), eq(null)))
                .thenReturn("ok");
        assertThat(callback.call("{\"skill\":\"alpha\",\"task\":\"t\"}")).isEqualTo("ok");
        verify(runnerService).runSkill(alpha, "t", null, null);
    }
}
