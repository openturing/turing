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
package com.viglet.turing.genai.nativeapi.voice;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T147 / §X.6.a — pluggable real-time voice transport seam, the voice analog of
 * {@link com.viglet.turing.genai.nativeapi.TurNativeProviderClient}. A provider
 * mints a short-lived session against a vendor's realtime speech endpoint
 * (OpenAI Realtime — {@code gpt-realtime} / {@code gpt-realtime-mini} /
 * {@code gpt-realtime-1.5}) so the browser can hold the WebSocket transport
 * directly without ever seeing the account API key.
 *
 * <p>Implementations are resolved by {@link TurRealtimeVoiceProviderFactory}
 * from a {@link TurLLMInstance}'s plugin type, mirroring how
 * {@code TurGenAiLlmProviderFactory} resolves chat/embedding providers. The
 * default deployment ships only the OpenAI implementation; other vendors fall
 * through to "voice unsupported".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurRealtimeVoiceProvider {

    /** Lowercased provider plugin type (matches {@code TurGenAiLlmProvider#getPluginType()}). */
    String getPluginType();

    /**
     * Whether {@code model} is a realtime model this provider can mint a session
     * for. Used to validate a caller-supplied model before hitting the vendor.
     */
    boolean supportsModel(String model);

    /**
     * The model used when the request leaves {@link TurRealtimeVoiceSessionRequest#model()}
     * blank.
     */
    String defaultModel();

    /**
     * Mint a real-time voice session for {@code instance}.
     *
     * @throws com.viglet.turing.genai.nativeapi.voice.TurRealtimeVoiceException on
     *         any credential / transport / vendor error — the caller maps this to
     *         a 4xx/5xx; there is no silent fallback (a voice request is explicit).
     */
    TurRealtimeVoiceSession createSession(TurLLMInstance instance,
            TurRealtimeVoiceSessionRequest request);
}
