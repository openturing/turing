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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.commons.utils.TurCommonsUtils;

/**
 * T321 — verifies {@link TurSkillSandboxCleanupTask} reaps idle session dirs by
 * mtime, keeps recently-touched ones, and prunes the now-empty parents.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillSandboxCleanupTaskTest {

    private TurSkillSandboxCleanupTask task;
    private Path tenantsRoot;

    @BeforeEach
    void setUp() throws IOException {
        task = new TurSkillSandboxCleanupTask();
        ReflectionTestUtils.setField(task, "enabled", true);
        ReflectionTestUtils.setField(task, "idleTtlHours", 24);
        Path sandboxRoot = Path.of(TurCommonsUtils.addSubDirToStoreDir(
                TurSkillSandboxSessionManager.SANDBOX_DIR).getPath());
        tenantsRoot = sandboxRoot.resolve(TurSkillSandboxSessionManager.TENANTS_DIR);
        deleteRecursively(tenantsRoot);
        Files.createDirectories(tenantsRoot);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(tenantsRoot);
    }

    @Test
    void reapsIdleSessionsAndKeepsFreshOnes() throws IOException {
        Path fresh = sessionDir("agentA", "conv-fresh", "skill1");
        Path stale = sessionDir("agentA", "conv-stale", "skill1");
        touchTree(fresh, System.currentTimeMillis());
        touchTree(stale, System.currentTimeMillis() - 48L * 3_600_000L);

        task.sweep();

        assertThat(Files.exists(fresh)).as("recently-used session survives").isTrue();
        assertThat(Files.exists(stale)).as("idle session reaped").isFalse();
        // Empty parent conversation dir is pruned.
        assertThat(Files.exists(stale.getParent())).isFalse();
    }

    @Test
    void disabledSweepDoesNothing() throws IOException {
        ReflectionTestUtils.setField(task, "enabled", false);
        Path stale = sessionDir("agentB", "conv1", "skill1");
        touchTree(stale, System.currentTimeMillis() - 48L * 3_600_000L);

        task.sweep();

        assertThat(Files.exists(stale)).isTrue();
    }

    // ---- helpers -------------------------------------------------------------

    private Path sessionDir(String agent, String conv, String skill) throws IOException {
        Path dir = tenantsRoot.resolve(agent).resolve(conv).resolve(skill).resolve("workspace");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("draft.txt"), "x");
        return dir.getParent(); // the per-skill session dir
    }

    private static void touchTree(Path root, long millis) throws IOException {
        try (var walk = Files.walk(root)) {
            walk.forEach(p -> p.toFile().setLastModified(millis));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
