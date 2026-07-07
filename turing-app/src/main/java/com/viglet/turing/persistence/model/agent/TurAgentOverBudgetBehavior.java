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
 * <p>{@link #COMPACT} routes the over-budget prompt through the T115-backed
 * {@code TurPromptCompactor} (summarizing the older middle turns) before the
 * LLM call, then proceeds best-effort even if the compaction can't bring the
 * estimate under budget.
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
     * Route the prompt through the T115-backed {@code TurPromptCompactor}
     * (summarize the older middle turns) before the LLM call, then proceed
     * best-effort even if it's still over budget.
     */
    COMPACT
}
