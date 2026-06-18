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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.utils.TurCommonsUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * T321 / §IX.4.d — scheduled sweeper for idle skill sandbox session directories.
 *
 * <p>{@link TurSkillSandboxSessionManager} persists a session's {@code skill/}
 * and {@code workspace/} directories under
 * {@code store/skill-sandbox/tenants/{agentId}/{conversationId}/{skillId}/} so
 * the filesystem survives across chat turns. Without a sweeper those persist
 * forever; this task deletes session directories whose most-recently-modified
 * file is older than the configured idle TTL, then prunes the now-empty
 * conversation and agent parents.
 *
 * <p>Reaping by mtime (rather than the Code Interpreter's date-bucket grain) is
 * what lets a long-lived conversation keep its workspace warm: any command that
 * writes to {@code /workspace} refreshes the mtime, so only genuinely idle
 * sessions are collected.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurSkillSandboxCleanupTask {

    @Value("${turing.code-interpreter.skill.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${turing.code-interpreter.skill.cleanup.idle-ttl-hours:24}")
    private int idleTtlHours;

    @Scheduled(fixedDelayString = "${turing.code-interpreter.skill.cleanup.fixed-delay-ms:3600000}",
            initialDelayString = "${turing.code-interpreter.skill.cleanup.initial-delay-ms:120000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        File tenantsRoot = TurCommonsUtils.addSubDirToStoreDir(
                TurSkillSandboxSessionManager.SANDBOX_DIR + "/" + TurSkillSandboxSessionManager.TENANTS_DIR);
        if (!tenantsRoot.isDirectory()) {
            return;
        }
        long cutoffMs = System.currentTimeMillis() - Math.max(1, idleTtlHours) * 3_600_000L;
        int reaped = 0;
        File[] agentDirs = tenantsRoot.listFiles(File::isDirectory);
        if (agentDirs == null) {
            return;
        }
        for (File agentDir : agentDirs) {
            File[] convDirs = agentDir.listFiles(File::isDirectory);
            if (convDirs != null) {
                for (File convDir : convDirs) {
                    reaped += sweepConversation(convDir, cutoffMs);
                    pruneIfEmpty(convDir);
                }
            }
            pruneIfEmpty(agentDir);
        }
        if (reaped > 0) {
            log.info("[SkillSandboxCleanup] reaped {} idle session(s) | idleTtl={}h", reaped, idleTtlHours);
        }
    }

    /** Each child is a per-skill session dir; reap those idle past the cutoff. */
    private int sweepConversation(File convDir, long cutoffMs) {
        File[] sessionDirs = convDir.listFiles(File::isDirectory);
        if (sessionDirs == null) {
            return 0;
        }
        int reaped = 0;
        for (File sessionDir : sessionDirs) {
            if (newestMtime(sessionDir.toPath()) < cutoffMs && deleteRecursively(sessionDir.toPath())) {
                reaped++;
            }
        }
        return reaped;
    }

    /** Most-recent last-modified time of any file in the tree (0 when empty/unreadable). */
    private static long newestMtime(Path root) {
        try (var walk = Files.walk(root)) {
            return walk.mapToLong(p -> p.toFile().lastModified()).max().orElse(0L);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void pruneIfEmpty(File dir) {
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            try {
                Files.deleteIfExists(dir.toPath());
            } catch (IOException e) {
                log.debug("[SkillSandboxCleanup] could not prune empty dir {}: {}", dir, e.getMessage());
            }
        }
    }

    private static boolean deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("[SkillSandboxCleanup] could not delete {}: {}", p, e.getMessage());
                }
            });
            return true;
        } catch (IOException e) {
            log.debug("[SkillSandboxCleanup] could not sweep {}: {}", path, e.getMessage());
            return false;
        }
    }
}
