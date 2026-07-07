/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

import java.util.List;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.7 / §X.8.a — the per-vendor seam behind {@link TurBatchInferenceService}.
 *
 * <p>Mirrors the {@code TurGenAiLlmProvider} pattern: each implementation
 * declares the {@link #getPluginType() plugin type} it serves and is resolved
 * through {@link TurBatchProviderFactory}. Only the vendors that expose a Batch
 * API implement this — OpenAI (Batch API + Files API JSONL) and Anthropic
 * (Message Batches). Ollama / Gemini / Azure have no batch endpoint and are
 * simply absent from the factory map.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBatchProvider {

    /** Lowercased provider plugin type (matches {@code TurGenAiLlmProvider#getPluginType()}). */
    String getPluginType();

    /**
     * Submit a chat batch and return the vendor batch id. The completion window
     * is the vendor default (24h). Throws on submission failure (no batch was
     * created); a created-but-failing batch surfaces later via {@link #status}.
     */
    String submitChat(TurLLMInstance instance, List<TurBatchChatRequest> requests);

    /** Current status of a previously-submitted batch. */
    TurBatchStatus status(TurLLMInstance instance, String vendorBatchId);

    /**
     * Fetch all per-request results of a completed batch. Should only be called
     * once {@link #status} reports {@link TurBatchState#COMPLETED}; returns one
     * {@link TurBatchChatResult} per submitted request.
     */
    List<TurBatchChatResult> results(TurLLMInstance instance, String vendorBatchId);

    /** Best-effort cancel of an in-flight batch. */
    void cancel(TurLLMInstance instance, String vendorBatchId);
}
