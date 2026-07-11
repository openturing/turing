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

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * The core inputs to {@link TurAgentChatExecutor#execute} for one agent-driven
 * chat turn — everything except the optional behavioural knobs (recursion
 * depth, forced A/B variant, skill-mode pin), which the {@code execute}
 * overloads carry separately. Bundled into one cohesive record so the executor
 * entry points stay below the parameter threshold instead of threading seven
 * positional arguments through every overload and call site.
 *
 * @param agent                the chatting agent
 * @param llmInstance          the resolved LLM instance for this turn
 * @param history              the conversation history (its last user message
 *                             is enriched with any {@code files})
 * @param systemPromptOverride nullable — when set, replaces the agent's own
 *                             system prompt (RAG flows embed retrieved chunks here)
 * @param conversationId       nullable — null means stateless turn / no T9 flow state
 * @param flowId               nullable — when set with {@code conversationId}, the
 *                             chat-flow engine governs this turn
 * @param files                nullable — visitor attachments for the current turn
 * @param requestPersonaId     nullable — T633 per-request persona override for
 *                             the anonymous public SN chat ("same question,
 *                             different eyes"); validated against the agent's
 *                             catalog by {@code TurAgentPersonaResolver} (unknown
 *                             → default). null on every non-demo turn.
 * @param untrustedCaller      T647 / §XXXVII.9 — {@code true} when the turn comes
 *                             from the anonymous public SN chat surface. When the
 *                             {@code turing.abuse.chat.anonymous-tools-enabled}
 *                             policy is off, the executor strips all tool
 *                             callbacks for such a caller. {@code false} for
 *                             authenticated agent/persona chat (default).
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurAgentChatRequest(
        TurAIAgent agent,
        TurLLMInstance llmInstance,
        List<ChatMessageItem> history,
        String systemPromptOverride,
        String conversationId,
        String flowId,
        List<MultipartFile> files,
        String requestPersonaId,
        boolean untrustedCaller) {

    /** Backward-compatible constructor — a trusted (authenticated) caller. */
    public TurAgentChatRequest(TurAIAgent agent, TurLLMInstance llmInstance, List<ChatMessageItem> history,
            String systemPromptOverride, String conversationId, String flowId,
            List<MultipartFile> files, String requestPersonaId) {
        this(agent, llmInstance, history, systemPromptOverride, conversationId, flowId, files,
                requestPersonaId, false);
    }
}
