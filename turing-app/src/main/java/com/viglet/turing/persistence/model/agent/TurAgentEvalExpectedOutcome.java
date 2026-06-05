/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * T285 / §XV.1 — the terminal outcome an eval case expects after the seed
 * turns are replayed through the chat-flow engine.
 *
 * <ul>
 *   <li>{@code CAPTURED} — the root flow reached a terminal {@code end} node
 *       (a successful capture / submission).</li>
 *   <li>{@code ABANDONED} — the conversation ended without reaching a real
 *       {@code end} node (user quit / stalled).</li>
 *   <li>{@code HANDOFF} — the flow escalated to a human / external channel.</li>
 *   <li>{@code ANY} — outcome is not asserted; only slot / rubric checks
 *       run. Default so a case authored from a single rubric doesn't
 *       accidentally fail on an outcome it never declared.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurAgentEvalExpectedOutcome {
    CAPTURED,
    ABANDONED,
    HANDOFF,
    ANY
}
