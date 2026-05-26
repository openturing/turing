/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import org.springframework.ai.chat.model.ChatModel;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Snapshot of inputs every {@link TurChatFlowGuardrailStrategy} needs to
 * decide a turn. Bundling them into a record keeps the strategy method
 * signature small and makes it cheap to introduce new fields when future
 * strategies (for example, structured-output JSON schema, RAG context)
 * require additional context.
 *
 * <p>Strategies <em>mutate</em> {@link #state} in place (set
 * {@code currentNodeId} and {@code variablesJson}); the engine is then
 * responsible for persistence and submission recording.
 *
 * @param state           the in-flight runtime state — strategies mutate this.
 * @param graph           the parsed graph the conversation is walking.
 * @param currentNode     the node the state currently points at.
 * @param userMessage     the user's last input ({@code ""} when there is none).
 * @param assistantMessage the LLM reply for this turn — for STRUCTURED_OUTPUT
 *                        this is the raw JSON the model emitted.
 * @param auxiliaryModel  a {@link ChatModel} a strategy may invoke for a
 *                        secondary call (LLM_JUDGE uses it). May be null
 *                        for strategies that don't need one.
 * @param flow            the active flow being walked. Carries flow-level
 *                        runtime settings a strategy may read — notably the
 *                        T51 {@code captureMode}. May be {@code null} in
 *                        unit tests that exercise a strategy without a
 *                        persisted flow; strategies must null-default it.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public record AdvanceContext(
        TurChatFlowState state,
        ChatFlowGraph graph,
        ChatFlowNode currentNode,
        String userMessage,
        String assistantMessage,
        ChatModel auxiliaryModel,
        TurChatFlow flow) {
}
