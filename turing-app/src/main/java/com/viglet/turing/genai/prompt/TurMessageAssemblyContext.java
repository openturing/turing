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

import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Block AL / §XXXV.3 (T616) — the inputs a {@link TurMessageContributor} reads to
 * decide whether (and what) history-prefix messages it contributes. Deliberately
 * narrower than {@link TurPromptAssemblyContext}: the memory sources key purely off
 * the agent's persisted conversation, not the system-prompt body or persona.
 *
 * @param agent             the chatting agent — read for the chat-memory /
 *                          compression / relevance knobs.
 * @param conversationId    persisted-memory key; blank means the contributors
 *                          no-op (nothing to read).
 * @param latestUserMessage the user's latest turn — the relevance scoring query;
 *                          blank skips relevance retrieval.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurMessageAssemblyContext(
        TurAIAgent agent,
        String conversationId,
        String latestUserMessage) {
}
