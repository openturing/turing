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
 * T147 / §X.6.a — what the caller wants from a real-time voice session. All
 * fields are optional hints: the provider falls back to its own defaults when a
 * value is blank (so the {@code /chat/voice} controller can pass only what the
 * agent configured).
 *
 * @param model        the realtime model id (e.g. {@code gpt-realtime}); blank →
 *                     the provider's default model.
 * @param voice        the synthesized voice name (e.g. {@code marin},
 *                     {@code cedar}); blank → the provider's default voice.
 * @param instructions the system prompt / persona the voice agent speaks with;
 *                     blank → no instructions sent (the model uses its default).
 * @param locale       BCP-47 locale hint for transcription/synthesis (e.g.
 *                     {@code pt-BR}); blank → the model auto-detects.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRealtimeVoiceSessionRequest(
        String model,
        String voice,
        String instructions,
        String locale) {
}
