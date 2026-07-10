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
import java.util.Locale;
import java.util.Map;

import com.viglet.turing.genai.TurSNGenAi.ConversationMessage;

/**
 * The per-turn request inputs to {@link TurSNGenAi#assistantConversationStreaming}
 * — the conversation {@code history}, the active facet {@code filters}, the
 * {@code conversationId}/{@code flowId} that bind the turn to a chat flow, the
 * optional A/B {@code forcedVariant} hint and the response {@code locale}.
 * Bundled into one record (separate from the resolved RAG context / agent / LLM
 * configuration) so the streaming entry point stays below the parameter
 * threshold.
 *
 * <p>T633 / §XXVII.4 added the optional {@code personaId} — a per-request
 * persona override for the anonymous public SN chat ("same question, different
 * eyes"). It is validated against the effective agent's catalog downstream
 * (unknown → default); null on every non-demo turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSNGenAiChatRequest(
        List<ConversationMessage> history,
        Map<String, List<String>> filters,
        String conversationId,
        String flowId,
        String forcedVariant,
        Locale locale,
        String personaId) {
}
