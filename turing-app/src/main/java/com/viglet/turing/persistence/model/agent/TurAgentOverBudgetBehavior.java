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
 * T123 / §IX.7 — what the executor does when the predicted prompt for a
 * chat turn exceeds the agent's {@code maxPromptTokens} budget. Stored as
 * a string column (see {@link TurAIAgent#overBudgetBehavior}) so
 * adding new modes later doesn't risk ordinal drift.
 *
 * <p>{@link #COMPACT} is reserved for a future revision that auto-triggers
 * the T115 workspace-backed history compression; the V1 path treats it as
 * a synonym for {@link #WARN} (proceed with a logged notice) so flow
 * authors can pre-pick the mode they'll want when T115 lands and the
 * upgrade just flips the runtime behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurAgentOverBudgetBehavior {
    /** Log a warning + proceed with the LLM call. Default. */
    WARN,
    /** Short-circuit the LLM call and surface an error to the caller. */
    ERROR,
    /**
     * Reserved for T115 workspace-backed compression. In V1 the executor
     * logs a "compact-not-implemented" notice and degrades to {@link #WARN}.
     */
    COMPACT
}
