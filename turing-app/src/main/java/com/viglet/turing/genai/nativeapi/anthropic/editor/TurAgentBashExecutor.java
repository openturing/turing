/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.editor;

/**
 * T142 / §X.4.d — the pluggable backend that executes the shell commands the
 * Anthropic {@code bash_20250124} tool emits during an agentic authoring loop.
 *
 * <p>Running arbitrary shell safely needs a sandbox (a container, a jailed
 * working directory) that isn't always present, so — like the computer-use
 * {@code TurComputerUseDriver} seam (T136) — bash execution is decoupled behind
 * this interface. Turing ships {@link TurNoOpAgentBashExecutor} (never
 * available); a real implementation (e.g. backed by the T80 Docker
 * code-interpreter sandbox) can be added later as a higher-priority bean. When
 * no executor is available the editor loop returns the command as an
 * errors-as-text {@code tool_result} rather than failing the turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurAgentBashExecutor {

    /**
     * Whether this executor can run commands in the current deployment. When
     * {@code false} the editor loop reports bash as unavailable instead of
     * calling {@link #run}.
     */
    boolean isAvailable();

    /**
     * Run one shell command scoped to a conversation's working area.
     *
     * @param agentId        owning agent id (for scoping the working directory)
     * @param conversationId owning conversation id
     * @param command        the shell command line the model asked to run
     * @return the captured result (stdout/stderr + exit status)
     */
    BashResult run(String agentId, String conversationId, String command);

    /**
     * The outcome of a bash command.
     *
     * @param output   combined stdout/stderr (or an explanatory message)
     * @param exitCode process exit code ({@code 0} = success)
     * @param error    {@code true} when the command failed or could not run
     */
    record BashResult(String output, int exitCode, boolean error) {

        public static BashResult ok(String output) {
            return new BashResult(output, 0, false);
        }

        public static BashResult failed(String output, int exitCode) {
            return new BashResult(output, exitCode, true);
        }
    }
}
