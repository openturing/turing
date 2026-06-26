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

/**
 * T147 / §X.6.a — a minted real-time voice session. This is exactly what the
 * browser needs to open the WebSocket transport to the vendor's realtime
 * endpoint and nothing more: a short-lived ephemeral client secret (never the
 * account API key), the resolved model + voice, the websocket URL and the
 * expiry.
 *
 * <p>The {@link #clientSecret()} is an <b>ephemeral</b> token (OpenAI mints an
 * {@code ek_…} value that expires in ~1 minute and is scoped to a single
 * realtime session), so it is safe to hand to the browser — the account key
 * itself never leaves the server.
 *
 * @param provider     plugin type that minted the session ({@code openai}).
 * @param clientSecret ephemeral token the browser sends as the Bearer credential
 *                     when opening the realtime WebSocket. Short-lived.
 * @param expiresAt    epoch-seconds at which {@link #clientSecret()} expires.
 * @param model        the resolved realtime model id the session is bound to.
 * @param voice        the resolved synthesized voice name.
 * @param wsUrl        the WebSocket URL the browser connects to (already carries
 *                     the {@code model} query parameter).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRealtimeVoiceSession(
        String provider,
        String clientSecret,
        long expiresAt,
        String model,
        String voice,
        String wsUrl) {
}
