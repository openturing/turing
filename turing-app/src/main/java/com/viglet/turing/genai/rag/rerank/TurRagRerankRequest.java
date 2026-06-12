/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;

/**
 * T337 / §XVII.1 — the input to a {@link TurRagRerankStrategy}.
 *
 * <p>{@code chatModel} rides in the request rather than the strategy method
 * signature because only {@link TurLlmRerankStrategy} needs it; the HTTP
 * strategies ({@code CROSS_ENCODER}, {@code COHERE}) ignore it. It may be
 * {@code null} — a strategy that requires it returns an empty list (the facade
 * then keeps retrieval order).
 *
 * @param query      the user query the chunks should answer (non-blank)
 * @param candidates the retrieved candidates, in retrieval order (non-empty)
 * @param topK       how many chunks to keep for stuffing ({@code > 0})
 * @param chatModel  the chat model for LLM-based ranking; may be {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurRagRerankRequest(String query, List<Document> candidates, int topK, ChatModel chatModel) {
}
