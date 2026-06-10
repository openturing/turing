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

/**
 * T321 / §IX.4.d — structured result of one {@code bash} command run inside a
 * skill sandbox session ({@link TurSkillSandboxService#runBash}).
 *
 * <p>Mirrors the shape of the Code Interpreter's structured result: typed
 * {@code stdout} / {@code stderr} / {@code exitCode} / {@code timedOut} /
 * {@code durationMs} so the activation harness (T322) and any programmatic
 * caller read fields directly instead of parsing text. {@link #combinedOutput()}
 * renders the LLM-facing blob a tool would feed back to the model.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSkillSandboxResult(
        String sessionId,
        String command,
        boolean success,
        int exitCode,
        boolean timedOut,
        long durationMs,
        String stdout,
        String stderr) {

    /**
     * The blob a {@code bash} tool feeds back to the model: stdout, then a
     * delimited stderr block when non-empty, then an exit/timeout footer when
     * the command did not succeed. Empty output renders as {@code "(no output)"}
     * so the model never sees a blank tool result.
     */
    public String combinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isBlank()) {
            sb.append(stdout.stripTrailing());
        }
        if (stderr != null && !stderr.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("--- stderr ---\n").append(stderr.stripTrailing());
        }
        if (timedOut) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("--- command timed out ---");
        } else if (!success) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("--- exited with code ").append(exitCode).append(" ---");
        }
        return sb.isEmpty() ? "(no output)" : sb.toString();
    }
}
