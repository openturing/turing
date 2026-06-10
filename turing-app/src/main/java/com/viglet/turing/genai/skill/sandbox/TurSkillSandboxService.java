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

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.tool.TurCodeInterpreterExecutionMode;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T321 / §IX.4.d — the skill sandbox <em>engine</em>: runs a {@code bash}
 * command inside an interactive, persistent skill session.
 *
 * <p>This is the evolution of the Code Interpreter from <em>snippet
 * execution</em> ("run this Python string, return output") to an
 * <em>interactive container session</em> the model drives across turns. Each
 * {@link #runBash} call spawns a fresh, hardened, throwaway container that
 * bind-mounts the session's two <b>stable</b> host directories — the skill
 * folder read-only at {@code /skill} and the persistent workspace read-write at
 * {@code /workspace} — so the filesystem persists between commands even though
 * the container does not.
 *
 * <p><b>Hardening</b> (mandatory — same posture as the T80 Code Interpreter
 * Docker mode): {@code --rm}, configurable {@code --network} (default
 * {@code none}), {@code --memory}/{@code --cpus}/{@code --pids-limit} caps,
 * {@code --cap-drop ALL}, {@code --security-opt no-new-privileges}, a
 * {@code --read-only} root fs with a small {@code /tmp} tmpfs, and the workspace
 * as the only writable mount.
 *
 * <p><b>Gating</b>: the feature requires object storage (skills live in storage)
 * <em>and</em> the Code Interpreter execution mode to be {@code DOCKER}. On a
 * {@code NATIVE} deployment {@link #isAvailable()} is false and {@link #runBash}
 * throws — there is no native fallback because a {@code bash}-driven session
 * with no container is exactly the runtime-escape vector containerization
 * exists to close.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurSkillSandboxService {

    private static final Logger log = LoggerFactory.getLogger(TurSkillSandboxService.class);

    private static final int MAX_OUTPUT_LENGTH = 30_000;
    private static final String DOCKER_IMAGE_FALLBACK = "python:3.12-slim";

    private final TurSkillSandboxSessionManager sessionManager;
    private final TurGlobalSettingsService globalSettingsService;

    public TurSkillSandboxService(TurSkillSandboxSessionManager sessionManager,
            TurGlobalSettingsService globalSettingsService) {
        this.sessionManager = sessionManager;
        this.globalSettingsService = globalSettingsService;
    }

    // ── Docker config — shared with the Code Interpreter Docker mode (T80) ──
    @Value("${turing.code-interpreter.docker.executable:docker}")
    private String dockerExecutable;
    @Value("${turing.code-interpreter.docker.network:none}")
    private String dockerNetwork;
    @Value("${turing.code-interpreter.docker.memory:512m}")
    private String dockerMemory;
    @Value("${turing.code-interpreter.docker.cpus:1.0}")
    private String dockerCpus;
    @Value("${turing.code-interpreter.docker.pids-limit:128}")
    private int dockerPidsLimit;

    /** Wall-clock timeout for a single skill command. */
    @Value("${turing.code-interpreter.skill.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * Whether the skill sandbox can run: object storage configured AND the
     * Code Interpreter execution mode is {@code DOCKER}. Never throws —
     * settings failures resolve to {@code false}.
     */
    public boolean isAvailable() {
        if (!sessionManager.isStorageEnabled()) {
            return false;
        }
        try {
            return globalSettingsService.getCodeInterpreterExecutionMode()
                    == TurCodeInterpreterExecutionMode.DOCKER;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run one {@code bash} command in the session's container and capture its
     * output. The command runs with the workspace as the working directory and
     * the skill folder available read-only at {@code /skill}.
     *
     * @throws IllegalStateException when the sandbox is unavailable (NATIVE mode
     *                               or storage disabled).
     */
    public TurSkillSandboxResult runBash(TurSkillSandboxSession session, String command) {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "Skill sandbox requires the Code Interpreter execution mode to be DOCKER "
                            + "and object storage to be configured.");
        }
        if (command == null || command.isBlank()) {
            return new TurSkillSandboxResult(session.sessionId(), command, false, -1, false, 0L,
                    "", "Error: empty command");
        }
        sessionManager.touch(session);
        String containerName = "turing-skill-" + session.sessionId() + "-"
                + Long.toHexString(System.nanoTime());
        List<String> cmd = buildDockerCommand(containerName, session, command, resolveImage());
        return execute(session.sessionId(), containerName, cmd);
    }

    private String resolveImage() {
        try {
            String image = globalSettingsService.getCodeInterpreterSkillImage();
            return (image == null || image.isBlank()) ? DOCKER_IMAGE_FALLBACK : image.trim();
        } catch (Exception e) {
            return DOCKER_IMAGE_FALLBACK;
        }
    }

    /**
     * Build the hardened {@code docker run … <image> bash -lc <command>} command.
     * Package-private so a unit test can assert the hardening flags and mounts
     * without a Docker daemon.
     */
    List<String> buildDockerCommand(String containerName, TurSkillSandboxSession session,
            String command, String image) {
        List<String> cmd = new ArrayList<>();
        cmd.add(dockerExecutable);
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--network");
        cmd.add(dockerNetwork);
        cmd.add("--memory");
        cmd.add(dockerMemory);
        cmd.add("--cpus");
        cmd.add(dockerCpus);
        cmd.add("--pids-limit");
        cmd.add(String.valueOf(dockerPidsLimit));
        cmd.add("--cap-drop");
        cmd.add("ALL");
        cmd.add("--security-opt");
        cmd.add("no-new-privileges");
        cmd.add("--read-only");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,size=64m");
        // Persistent workspace (rw) — survives across turns — is the ONLY
        // writable bind mount. The skill folder is mounted read-only.
        cmd.add("-v");
        cmd.add(session.workspaceDir().getAbsolutePath() + ":" + TurSkillSandboxSession.WORKSPACE_MOUNT + ":rw");
        cmd.add("-v");
        cmd.add(session.skillDir().getAbsolutePath() + ":" + TurSkillSandboxSession.SKILL_MOUNT + ":ro");
        cmd.add("-w");
        cmd.add(TurSkillSandboxSession.WORKSPACE_MOUNT);
        cmd.add("-e");
        cmd.add("HOME=/tmp");
        cmd.add("-e");
        cmd.add("MPLBACKEND=Agg");
        cmd.add("-e");
        cmd.add("MPLCONFIGDIR=/tmp");
        cmd.add("-e");
        cmd.add("PYTHONUTF8=1");
        cmd.add("-e");
        cmd.add("PYTHONIOENCODING=utf-8");
        // SKILL_DIR lets scripts reference the mounted folder portably.
        cmd.add("-e");
        cmd.add("SKILL_DIR=" + TurSkillSandboxSession.SKILL_MOUNT);
        cmd.add(image);
        cmd.add("bash");
        cmd.add("-lc");
        cmd.add(command);
        return cmd;
    }

    private TurSkillSandboxResult execute(String sessionId, String containerName, List<String> cmd) {
        long startNanos = System.nanoTime();
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            process = pb.start();
        } catch (IOException e) {
            log.error("[SkillSandbox] session={} | could not start container: {}", sessionId, e.getMessage());
            return new TurSkillSandboxResult(sessionId, lastArg(cmd), false, -1, false,
                    elapsedMs(startNanos), "", "Error starting skill sandbox container: " + e.getMessage());
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outThread = Thread.ofVirtual().start(() -> readStream(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8), stdout));
        Thread errThread = Thread.ofVirtual().start(() -> readStream(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8), stderr));

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            killContainer(containerName);
            return new TurSkillSandboxResult(sessionId, lastArg(cmd), false, -1, false,
                    elapsedMs(startNanos), stdout.toString(), "Error: skill sandbox interrupted");
        }

        if (!finished) {
            process.destroyForcibly();
            // Killing the `docker run` client doesn't stop the container.
            killContainer(containerName);
            joinQuietly(outThread, errThread);
            long durationMs = elapsedMs(startNanos);
            log.warn("[SkillSandbox] session={} | TIMEOUT after {}s | container={}",
                    sessionId, timeoutSeconds, containerName);
            return new TurSkillSandboxResult(sessionId, lastArg(cmd), false, -1, true, durationMs,
                    stdout.toString(), stderr.toString());
        }

        joinQuietly(outThread, errThread);
        int exitCode = process.exitValue();
        long durationMs = elapsedMs(startNanos);
        log.info("[SkillSandbox] session={} | exit={} | {}ms | stdout={} chars",
                sessionId, exitCode, durationMs, stdout.length());
        return new TurSkillSandboxResult(sessionId, lastArg(cmd), exitCode == 0, exitCode, false,
                durationMs, stdout.toString(), stderr.toString());
    }

    private void killContainer(String containerName) {
        try {
            Process kill = new ProcessBuilder(dockerExecutable, "kill", containerName)
                    .redirectErrorStream(true).start();
            kill.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.debug("[SkillSandbox] docker kill {} failed: {}", containerName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readStream(java.io.Reader reader, StringBuilder buffer) {
        try (var br = new java.io.BufferedReader(reader)) {
            br.lines().forEach(line -> {
                if (buffer.length() < MAX_OUTPUT_LENGTH) {
                    buffer.append(line).append('\n');
                }
            });
        } catch (IOException e) {
            // ignore stream read errors
        }
    }

    private static void joinQuietly(Thread... threads) {
        for (Thread t : threads) {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** The bash command is always the last arg of the docker command. */
    private static String lastArg(List<String> cmd) {
        return cmd.isEmpty() ? "" : cmd.get(cmd.size() - 1);
    }
}
