/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Response of the session-messages API. {@code engine} reports which
 * {@code turing.logging.engine} is wired up ({@code MONGODB}, {@code REDIS}
 * or {@code NONE}); {@code enabled} is {@code false} when chat memory is
 * globally off — in that case {@code messages} is always empty.
 *
 * <p>{@code memory} carries an AI-generated summary of the transcript when
 * the caller passes {@code summary=true} (and a default LLM is configured).
 * It is {@code null} otherwise so the LLM is not invoked on every request.
 * The {@link TurLlmSummaryService.SummaryResult#content} is plain text /
 * markdown; {@code canRegenerate} mirrors the global setting that controls
 * whether the UI may bypass the cache via {@code regenerate=true}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionMessagesDto(
        String conversationId,
        String engine,
        boolean enabled,
        List<TurChatSessionMessageDto> messages,
        TurLlmSummaryService.SummaryResult memory) {
}
