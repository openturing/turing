/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.genai;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * The identity, attribution and timing of a single chat turn, shared by every
 * branch of {@link TurChatStreamingDispatcher} (the STREAM/CALL dispatch plus
 * the stream-completion telemetry). Bundled into one record so those methods
 * carry only their branch-specific arguments as loose parameters and stay below
 * the parameter threshold.
 *
 * @param agent           the chatting agent (telemetry + STREAM gating)
 * @param llmInstance     the resolved LLM instance for this turn
 * @param conversationId  nullable — null means stateless turn
 * @param lastUserMessage the latest user message (telemetry)
 * @param username        resolved from the {@code SecurityContext} (token usage attribution)
 * @param tStart          turn-start millis (for total-time accounting)
 * @param tSetupEnd       setup-phase end millis (for post-time accounting)
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatTurnContext(
        TurAIAgent agent,
        TurLLMInstance llmInstance,
        String conversationId,
        String lastUserMessage,
        String username,
        long tStart,
        long tSetupEnd) {
}
