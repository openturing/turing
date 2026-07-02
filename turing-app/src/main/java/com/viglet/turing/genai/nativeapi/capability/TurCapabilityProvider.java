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
package com.viglet.turing.genai.nativeapi.capability;

import java.util.Locale;

/**
 * T432 / §X.18 — who executes a capability. {@link #TURING} is an in-JVM
 * {@code @Tool} callback (provider-agnostic); {@link #OPENAI} / {@link #ANTHROPIC}
 * are server-side tools executed inside the vendor's infrastructure. The
 * provider, paired with a capability's {@code function}, is what lets the picker
 * group "Turing code-interpreter", "OpenAI code_interpreter" and "Anthropic
 * code_execution" into one mutually-exclusive row.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurCapabilityProvider {
    TURING,
    OPENAI,
    ANTHROPIC,
    GEMINI,
    ANY;

    /**
     * Maps a {@code TurGenAiLlmProvider#getPluginType()} string (e.g.
     * {@code "openai"}, {@code "anthropic"}) to a provider. Unknown / null
     * plugin types map to {@link #ANY}.
     */
    public static TurCapabilityProvider fromPluginType(String pluginType) {
        if (pluginType == null) {
            return ANY;
        }
        return switch (pluginType.trim().toLowerCase(Locale.ROOT)) {
            case "openai" -> OPENAI;
            case "anthropic" -> ANTHROPIC;
            case "gemini" -> GEMINI;
            case "turing" -> TURING;
            default -> ANY;
        };
    }
}
