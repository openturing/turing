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
package com.viglet.turing.domain.llm;

import java.util.Locale;
import java.util.Set;

/**
 * Type-safe identifier for an LLM plugin (OpenAI, Anthropic, Ollama, etc.).
 * Wraps the raw string identifier that was previously passed around as a
 * loose {@code String} — callers had to remember the lower-case convention
 * and trust providers not to disagree on spelling.
 *
 * <p>Construction is restricted to the canonical form: lower-case, trimmed,
 * non-blank. Callers that already hold a normalised value can use
 * {@link #of(String)}; callers receiving raw user input should use the same
 * factory and rely on the validation it performs.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record LlmProviderType(String value) {

    /**
     * Plugin identifiers shipped with the platform. Custom plugins can still
     * register their own type — this set exists only to surface common typos
     * during code review and tests, not to gate runtime resolution.
     */
    public static final Set<String> KNOWN = Set.of(
            "openai",
            "anthropic",
            "ollama",
            "gemini",
            "gemini-openai",
            "openai-compatible",
            "bedrock",
            "voyage",
            "cohere",
            "mistral",
            "vertex-ai");

    public LlmProviderType {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LlmProviderType value must not be blank");
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "LlmProviderType must be lower-case; got: " + value);
        }
    }

    /** Normalises and returns a {@link LlmProviderType}. */
    public static LlmProviderType of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("LlmProviderType raw value must not be null");
        }
        return new LlmProviderType(raw.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isKnown() {
        return KNOWN.contains(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
