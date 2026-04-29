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
 * How the engine enforces a chat flow at runtime: how the system prompt is
 * augmented for the current node, how the response is judged on-topic, and
 * how the next node is chosen.
 *
 * <p>Phase B step 1 ships only {@link #HEURISTIC}. Future strategies
 * ({@code LLM_JUDGE}, {@code STRUCTURED_OUTPUT}, …) will be added as
 * additional values without changing the column type.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public enum TurChatFlowGuardrailMethod {

    /**
     * Prompt-only guard rail: the current node's instruction is injected as a
     * firm contract in the system prompt, and the engine advances to the
     * first outgoing edge with a lightweight alignment heuristic on the
     * assistant response. No extra LLM calls.
     */
    HEURISTIC,

    /**
     * Same prompt-level contract as {@link #HEURISTIC}, but adds a second
     * LLM-as-judge call after the chat reply that returns a small JSON
     * verdict ({@code on_topic}, {@code collected_value},
     * {@code ready_to_advance}). When the judge marks the response as
     * off-topic, the engine substitutes a redirect message; when the user
     * supplied a valid value, the engine stores it and advances. ~200 extra
     * tokens per turn while a flow is active.
     */
    LLM_JUDGE,

    /**
     * Single-call structured output: the chat LLM is instructed to reply
     * with a single JSON object that bundles the user-facing text and the
     * verdict fields ({@code reply}, {@code on_topic}, {@code collected_value},
     * {@code ready_to_advance}, {@code abandoned}). The engine extracts
     * {@code reply} for display and applies the verdict — same enforcement
     * as {@link #LLM_JUDGE} but without the second call. Most modern
     * providers (OpenAI, Gemini, Ollama) follow JSON instructions reliably;
     * Anthropic falls back gracefully when parsing fails.
     */
    STRUCTURED_OUTPUT
}
