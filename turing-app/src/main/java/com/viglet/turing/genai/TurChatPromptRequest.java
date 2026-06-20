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

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * The core inputs to {@link TurChatPromptAssembler#assemble} for one turn —
 * everything except the optional file attachments and skill-mode pin, which the
 * per-turn overloads carry separately. Bundled into one cohesive record so the
 * assemble entry points stay below the parameter threshold instead of threading
 * seven positional arguments through every overload and call site.
 *
 * @param agent            the chatting agent (used by T30 for relevance opt-in)
 * @param history          the persisted conversation history (untouched by the assembler)
 * @param conversationId   nullable — null means stateless turn / no T30 enrichment
 * @param flowContext      nullable — null means no chat flow governs this turn
 * @param effectivePersona the post-walk persona (single resolution per §I.5 step 2)
 * @param lastUserMessage  the latest user message (drives T30 relevance + few-shot examples)
 * @param baseSystemPrompt either the agent's own system prompt or a RAG override
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatPromptRequest(
        TurAIAgent agent,
        List<ChatMessageItem> history,
        String conversationId,
        TurAgentChatFlowContext flowContext,
        TurPersona effectivePersona,
        String lastUserMessage,
        String baseSystemPrompt) {
}
