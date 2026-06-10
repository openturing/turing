/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T321 — verifies {@link TurSkillSandboxService} gates on Docker mode, builds a
 * hardened {@code docker run … bash -lc} command with the right mounts, and
 * short-circuits empty commands.
 *
 * <p>No Docker daemon is exercised: {@link TurSkillSandboxService#buildDockerCommand}
 * is asserted directly, and {@link TurSkillSandboxService#runBash} is only driven
 * down the gate/empty-command paths that never spawn a process.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSkillSandboxServiceTest {

    @Mock
    TurSkillSandboxSessionManager sessionManager;

    @Mock
    TurGlobalSettingsService globalSettingsService;

    private TurSkillSandboxService service;

    @BeforeEach
    void setUp() {
        service = new TurSkillSandboxService(sessionManager, globalSettingsService);
        ReflectionTestUtils.setField(service, "dockerExecutable", "docker");
        ReflectionTestUtils.setField(service, "dockerNetwork", "none");
        ReflectionTestUtils.setField(service, "dockerMemory", "512m");
        ReflectionTestUtils.setField(service, "dockerCpus", "1.0");
        ReflectionTestUtils.setField(service, "dockerPidsLimit", 128);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 120);
    }

    private TurSkillSandboxSession sampleSession() {
        return new TurSkillSandboxSession("sess1234", "skill-id", "agentA", "conv1",
                new File("/store/skill-sandbox/tenants/agentA/conv1/skill-id"),
                new File("/store/skill-sandbox/tenants/agentA/conv1/skill-id/skill"),
                new File("/store/skill-sandbox/tenants/agentA/conv1/skill-id/workspace"));
    }

    @Test
    void availableOnlyWhenStorageEnabledAndDockerMode() {
        when(sessionManager.isStorageEnabled()).thenReturn(true);
        when(globalSettingsService.getCodeInterpreterExecutionMode())
                .thenReturn(TurCodeInterpreterExecutionMode.DOCKER);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void unavailableInNativeMode() {
        when(sessionManager.isStorageEnabled()).thenReturn(true);
        when(globalSettingsService.getCodeInterpreterExecutionMode())
                .thenReturn(TurCodeInterpreterExecutionMode.NATIVE);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void unavailableWhenStorageDisabled() {
        when(sessionManager.isStorageEnabled()).thenReturn(false);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void runBashThrowsWhenUnavailable() {
        when(sessionManager.isStorageEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.runBash(sampleSession(), "echo hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCKER");
    }

    @Test
    void emptyCommandReturnsErrorWithoutRunning() {
        when(sessionManager.isStorageEnabled()).thenReturn(true);
        when(globalSettingsService.getCodeInterpreterExecutionMode())
                .thenReturn(TurCodeInterpreterExecutionMode.DOCKER);

        TurSkillSandboxResult result = service.runBash(sampleSession(), "   ");

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).contains("empty command");
    }

    @Test
    void buildsHardenedDockerCommandWithSkillAndWorkspaceMounts() {
        List<String> cmd = service.buildDockerCommand("turing-skill-sess1234-ab",
                sampleSession(), "ls /skill", "python:3.12-slim");

        // Hardening flags present.
        assertThat(cmd).containsSubsequence("docker", "run", "--rm");
        assertThat(cmd).containsSubsequence("--network", "none");
        assertThat(cmd).containsSubsequence("--memory", "512m");
        assertThat(cmd).containsSubsequence("--cpus", "1.0");
        assertThat(cmd).containsSubsequence("--pids-limit", "128");
        assertThat(cmd).containsSubsequence("--cap-drop", "ALL");
        assertThat(cmd).containsSubsequence("--security-opt", "no-new-privileges");
        assertThat(cmd).contains("--read-only");
        assertThat(cmd).contains("/tmp:rw,size=64m");
        // Skill folder mounted read-only, workspace read-write.
        assertThat(cmd).anyMatch(a -> a.endsWith("/skill:ro"));
        assertThat(cmd).anyMatch(a -> a.endsWith("/workspace:rw"));
        // Working dir is the persistent workspace.
        assertThat(cmd).containsSubsequence("-w", "/workspace");
        // The command is the last arg, driven through bash -lc.
        assertThat(cmd).containsSubsequence("bash", "-lc", "ls /skill");
        assertThat(cmd.get(cmd.size() - 1)).isEqualTo("ls /skill");
        // Image precedes the bash invocation.
        assertThat(cmd).containsSubsequence("python:3.12-slim", "bash");
    }

    @Test
    void combinedOutputRendersStderrAndExitFooter() {
        TurSkillSandboxResult result = new TurSkillSandboxResult("s", "false", false, 1, false,
                10L, "partial", "boom");
        assertThat(result.combinedOutput())
                .contains("partial")
                .contains("--- stderr ---")
                .contains("boom")
                .contains("exited with code 1");
    }

    @Test
    void combinedOutputOfEmptySuccessIsNoOutput() {
        TurSkillSandboxResult result = new TurSkillSandboxResult("s", "true", true, 0, false,
                5L, "", "");
        assertThat(result.combinedOutput()).isEqualTo("(no output)");
    }
}
