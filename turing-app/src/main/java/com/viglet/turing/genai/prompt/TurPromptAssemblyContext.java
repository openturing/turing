/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt;

import com.viglet.turing.genai.TurAgentChatFlowContext;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Block AL / §XXXV.1 — the inputs every {@link TurPromptContributor} reads to
 * decide whether (and what) it contributes to the turn's system prompt. It is
 * the union of what {@code TurChatPromptRequest} and the assemble overloads
 * carried, minus the history/attachment handling (which stays on the assembler,
 * as it fans out into user/assistant messages rather than the system message).
 *
 * @param agent            the chatting agent — the source of capability flags,
 *                         MCP servers, skills.
 * @param persona          the resolved (post-flow-walk) persona for this turn, or
 *                         {@code null} when none applies.
 * @param embeddingModel   the model used to embed the user query for persona
 *                         few-shot retrieval; {@code null} skips few-shot (as in a
 *                         preview with no live query).
 * @param userQuery        the latest user message — drives few-shot similarity;
 *                         {@code null}/blank skips few-shot.
 * @param baseSystemPrompt the agent's own system prompt or a RAG override — the
 *                         body the persona wraps.
 * @param flowContext      the resolved chat-flow runtime, or {@code null} when no
 *                         flow governs the turn.
 * @param selectedSkillId  optional single-skill pin (T325); {@code null}/blank
 *                         offers all enabled skills.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptAssemblyContext(
        TurAIAgent agent,
        TurPersona persona,
        TurEmbeddingModel embeddingModel,
        String userQuery,
        String baseSystemPrompt,
        TurAgentChatFlowContext flowContext,
        String selectedSkillId) {
}
