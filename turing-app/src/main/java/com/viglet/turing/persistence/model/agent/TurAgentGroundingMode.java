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
 * Per-agent grounding policy. Decides whether an agent is a general-purpose
 * assistant (may generate code, call tools, answer from outside knowledge) or a
 * strictly retrieval-grounded assistant that answers ONLY from the retrieved
 * website content and refuses everything else.
 *
 * <p>The value is a first-class agent parameter (not a phrasing choice buried in
 * the editable system prompt) precisely so the grounding cannot be lost by
 * editing the prompt, and survives an export/import round-trip. When
 * {@link #STRICT_RAG} is set, {@code TurSNGenAi} prepends a non-removable guard
 * to the system prompt at runtime (the configured prompt becomes additive), the
 * same way retrieved content is always fenced by the untrusted-content guard.
 *
 * <p>Stored as a {@code STRING} column (see {@link TurAIAgent#groundingMode}) so
 * adding modes later never risks ordinal drift.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurAgentGroundingMode {

    /**
     * Default — legacy, flexible behaviour. The agent may generate code, call
     * native/MCP/custom tools, and use the configured system prompt verbatim
     * (or the sensible default when blank). Existing agents deserialize to this,
     * so their behaviour is unchanged.
     */
    OPEN,

    /**
     * Strict retrieval-grounded mode. The agent answers ONLY from the retrieved
     * website content: a non-removable guard is prefixed to the system prompt
     * instructing the model to refuse code generation, arbitrary tasks, and any
     * "new instruction" / role-change attempt, and to decline when the answer is
     * not present in the retrieved content. The operator's configured prompt is
     * still applied, but only additively (tone / domain framing) — it cannot
     * relax the grounding.
     */
    STRICT_RAG
}
