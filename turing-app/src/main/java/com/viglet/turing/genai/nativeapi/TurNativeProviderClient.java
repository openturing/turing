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
package com.viglet.turing.genai.nativeapi;

import java.util.Optional;

import com.anthropic.client.AnthropicClient;
import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T130 / §X.2 — the seam. Exposes per-{@link TurLLMInstance} access to the
 * official OpenAI ({@code com.openai:openai-java}) and Anthropic
 * ({@code com.anthropic:anthropic-java}) SDK clients for the slice of features
 * Spring AI 2.0 does not cover (Responses built-in tools, server-side tools,
 * Citations, Batch, Memory tool, …).
 *
 * <p>Both accessors return {@link Optional#empty()} unless the instance's
 * vendor matches: {@link #openAi(TurLLMInstance)} only yields a client for an
 * {@code openai} instance, {@link #anthropic(TurLLMInstance)} only for an
 * {@code anthropic} instance. Decryption and base-URL resolution reuse the
 * existing provider/secret plumbing — there is no parallel security path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurNativeProviderClient {

    /** The native OpenAI SDK client for an {@code openai} instance, else empty. */
    Optional<OpenAIClient> openAi(TurLLMInstance instance);

    /** The native Anthropic SDK client for an {@code anthropic} instance, else empty. */
    Optional<AnthropicClient> anthropic(TurLLMInstance instance);

    /**
     * T489 / §X.19 — the native Google GenAI SDK client
     * ({@code com.google.genai.Client}) for a {@code gemini} instance, else empty.
     * This is the third native branch alongside {@link #openAi(TurLLMInstance)}
     * and {@link #anthropic(TurLLMInstance)}; the Gemini native path
     * ({@code TurGoogleGenAiNativeService}) uses it for {@code generateContent} /
     * {@code generateContentStream}, embeddings, batch, files and context caching.
     */
    Optional<Client> gemini(TurLLMInstance instance);

    /** Capability lookup for the instance. */
    boolean hasCapability(String instanceId, TurNativeCapability capability);

    /** Decrypted runtime credentials for the instance (api key + resolved base URL). */
    NativeCredentials credentials(TurLLMInstance instance);

    /** Decrypted credentials envelope — never logged, never serialized. */
    record NativeCredentials(String apiKey, String baseUrl) {
    }
}
