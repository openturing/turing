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

import java.util.Locale;
import java.util.Optional;

/**
 * F.1/F.2 / §X.2-§X.3 — the catalogue of native (non-Spring-AI) LLM
 * capabilities a {@link com.viglet.turing.persistence.model.llm.TurLLMInstance}
 * can opt into.
 *
 * <p>Each constant carries a stable {@link #getKey() key} (the value stored in
 * {@code llm_instance_capability.capabilityKey}) and the
 * {@link #getPluginType() plugin type} of the provider that implements it.
 * Capabilities are bound to a single vendor: the OpenAI Responses built-in
 * tools (F.2, §X.3) are all {@code openai}; the Anthropic server-side tools
 * (F.3, §X.4) will be added here keyed to {@code anthropic} when that block
 * ships.
 *
 * <p>Adding a capability here makes it visible in the capability-matrix API
 * and selectable per instance; whether it is <em>wired</em> into a request is
 * decided by the vendor's native chat path. The OpenAI Responses path wires
 * {@code web_search} / {@code file_search} / {@code code_interpreter} /
 * {@code image_generation} / {@code mcp}. {@code computer_use} is listed (so
 * the matrix can advertise it) but deferred — it needs a multi-turn
 * screenshot/action loop, not a one-shot server-side tool.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurNativeCapability {

    /** T133 / §X.3.a — OpenAI Responses {@code web_search} built-in tool. */
    OPENAI_WEB_SEARCH("openai-web-search", "openai"),
    /** T134 / §X.3.b — OpenAI Responses {@code file_search} (hosted vector stores). */
    OPENAI_FILE_SEARCH("openai-file-search", "openai"),
    /** T135 / §X.3.c — OpenAI Responses {@code code_interpreter} (managed container). */
    OPENAI_CODE_INTERPRETER("openai-code-interpreter", "openai"),
    /** T137 / §X.3.e — OpenAI Responses {@code image_generation} inline tool. */
    OPENAI_IMAGE_GENERATION("openai-image-generation", "openai"),
    /** T138 / §X.3.f — OpenAI Responses remote {@code mcp} tool (Streamable HTTP / SSE). */
    OPENAI_MCP("openai-mcp", "openai"),
    /** T136 / §X.3.d — OpenAI Responses {@code computer_use}. Advertised but
     *  not yet wired (needs the screenshot/action loop). */
    OPENAI_COMPUTER_USE("openai-computer-use", "openai");

    private final String key;
    private final String pluginType;

    TurNativeCapability(String key, String pluginType) {
        this.key = key;
        this.pluginType = pluginType;
    }

    public String getKey() {
        return key;
    }

    /** Lowercased provider plugin type (matches {@code TurGenAiLlmProvider#getPluginType()}). */
    public String getPluginType() {
        return pluginType;
    }

    public boolean isForPlugin(String candidatePluginType) {
        return candidatePluginType != null
                && pluginType.equals(candidatePluginType.toLowerCase(Locale.ROOT));
    }

    public static Optional<TurNativeCapability> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (TurNativeCapability capability : values()) {
            if (capability.key.equals(normalized)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }
}
