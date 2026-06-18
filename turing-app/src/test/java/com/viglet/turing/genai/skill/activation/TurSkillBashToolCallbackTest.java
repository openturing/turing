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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxResult;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSession;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSessionManager;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T322 — verifies {@code skill_bash} gates on sandbox availability, opens the
 * session scoped by the tool context, runs the command, and reports errors as
 * text rather than throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSkillBashToolCallbackTest {

    @Mock
    TurSkillSandboxSessionManager sessionManager;
    @Mock
    TurSkillSandboxService sandboxService;

    private TurSkill alpha;
    private TurSkillBashToolCallback callback;

    @BeforeEach
    void setUp() {
        alpha = new TurSkill();
        alpha.setId("id-alpha");
        alpha.setName("alpha");
        callback = new TurSkillBashToolCallback(List.of(alpha), sessionManager, sandboxService);
    }

    private TurSkillSandboxSession session() {
        return new TurSkillSandboxSession("s1", "id-alpha", "agentA", "conv1",
                new File("/x"), new File("/x/skill"), new File("/x/workspace"));
    }

    private ToolContext ctx() {
        return new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, "agentA",
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, "conv1"));
    }

    @Test
    void hasExpectedDefinition() {
        assertThat(callback.getToolDefinition().name()).isEqualTo("skill_bash");
        assertThat(callback.getToolDefinition().inputSchema()).contains("command");
    }

    @Test
    void unavailableSandboxReturnsMessage() {
        when(sandboxService.isAvailable()).thenReturn(false);
        assertThat(callback.call("{\"skill\":\"alpha\",\"command\":\"ls\"}"))
                .contains("unavailable").contains("DOCKER");
        verifyNoInteractions(sessionManager);
    }

    @Test
    void runsCommandInScopedSession() {
        when(sandboxService.isAvailable()).thenReturn(true);
        TurSkillSandboxSession session = session();
        when(sessionManager.openSession("agentA", "conv1", "id-alpha")).thenReturn(session);
        when(sandboxService.runBash(session, "ls /skill"))
                .thenReturn(new TurSkillSandboxResult("s1", "ls /skill", true, 0, false, 5L,
                        "SKILL.md\nscripts", ""));

        String out = callback.call("{\"skill\":\"alpha\",\"command\":\"ls /skill\"}", ctx());

        assertThat(out).contains("SKILL.md").contains("scripts");
        verify(sessionManager).openSession("agentA", "conv1", "id-alpha");
    }

    @Test
    void missingCommandReturnsError() {
        when(sandboxService.isAvailable()).thenReturn(true);
        assertThat(callback.call("{\"skill\":\"alpha\"}", ctx()))
                .contains("requires a non-blank 'command'");
        verifyNoInteractions(sessionManager);
    }

    @Test
    void unknownSkillReturnsError() {
        when(sandboxService.isAvailable()).thenReturn(true);
        assertThat(callback.call("{\"skill\":\"ghost\",\"command\":\"ls\"}", ctx()))
                .contains("No skill named 'ghost'");
        verifyNoInteractions(sessionManager);
    }

    @Test
    void sessionFailureIsReportedNotThrown() {
        when(sandboxService.isAvailable()).thenReturn(true);
        lenient().when(sessionManager.openSession(any(), any(), eq("id-alpha")))
                .thenThrow(new IllegalStateException("storage off"));
        assertThat(callback.call("{\"skill\":\"alpha\",\"command\":\"ls\"}", ctx()))
                .contains("Failed to run command").contains("storage off");
    }
}
