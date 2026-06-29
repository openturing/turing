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
 * T166 / §X.9.d — scope for the Anthropic {@code memory_20250818} tool (T163)
 * file area. Decides whether the {@code memory/} scratchpad an agent reads and
 * writes is isolated per conversation (the default) or shared across every
 * conversation a given end user has with the agent.
 *
 * <p>Stored as a {@code STRING} column (see {@link TurAIAgent#memoryScope}) so
 * adding scopes later never risks ordinal drift.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurMemoryScope {

    /**
     * Per-conversation memory (default — preserves the T163 behaviour). The
     * memory files live under the live conversation's workspace and vanish with
     * it; nothing leaks between conversations.
     */
    CONVERSATION,

    /**
     * Per-user persistent memory. The memory files are keyed by the
     * authenticated end user (Keycloak {@code sub}) instead of the conversation,
     * so the agent accumulates stable preferences ("Alex prefers terse answers")
     * across every conversation that user has with it. Falls back to
     * {@link #CONVERSATION} when no stable user identity is resolvable (e.g. an
     * anonymous visitor turn), so it never writes user memory under an
     * unattributable scope.
     */
    USER
}
