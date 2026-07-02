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

import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.BASH;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.CODE_EXEC;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.COMPUTER_USE;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.FILE_SEARCH;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.IMAGE_GEN;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.MCP;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.MEMORY;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.TEXT_EDITOR;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.VOICE;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.WEB_FETCH;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions.WEB_SEARCH;
import static com.viglet.turing.genai.nativeapi.capability.TurCapabilityKind.TOOL;

import java.util.Locale;
import java.util.Optional;

import com.viglet.turing.genai.nativeapi.capability.TurCapabilityKind;
import com.viglet.turing.genai.nativeapi.capability.TurCapabilityProvider;

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
 * (F.3, §X.4) are keyed to {@code anthropic}.
 *
 * <p>T432 / §X.18 — each constant is also tagged with the registry taxonomy:
 * its {@link #getKind() kind} (all current natives are {@link TurCapabilityKind#TOOL}),
 * its abstract {@link #getFunction() function} (so cross-vendor and cross-source
 * alternatives group into one mutually-exclusive row in the picker), a
 * {@link #getCategory() category} for visual layout, and {@link #isOwnsTurn()}
 * for the capabilities (computer_use) that take the whole turn. The unified
 * {@code TurCapabilityRegistry} reads these to build the descriptor list both
 * UIs render from.
 *
 * <p>Adding a capability here makes it visible in the capability-matrix API
 * and selectable per instance; whether it is <em>wired</em> into a request is
 * decided by the vendor's native chat path. The OpenAI Responses path wires
 * {@code web_search} / {@code file_search} / {@code code_interpreter} /
 * {@code image_generation} / {@code mcp} as one-shot server-side tools.
 * {@code computer_use} (T136) is wired separately through the multi-turn
 * screenshot/action loop in
 * {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService},
 * driving a pluggable
 * {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurNativeCapability {

    /** T133 / §X.3.a — OpenAI Responses {@code web_search} built-in tool. */
    OPENAI_WEB_SEARCH("openai-web-search", "openai", TOOL, WEB_SEARCH, "Web", false),
    /** T134 / §X.3.b — OpenAI Responses {@code file_search} (hosted vector stores). */
    OPENAI_FILE_SEARCH("openai-file-search", "openai", TOOL, FILE_SEARCH, "Documents", false),
    /** T135 / §X.3.c — OpenAI Responses {@code code_interpreter} (managed container). */
    OPENAI_CODE_INTERPRETER("openai-code-interpreter", "openai", TOOL, CODE_EXEC, "Code", false),
    /** T137 / §X.3.e — OpenAI Responses {@code image_generation} inline tool. */
    OPENAI_IMAGE_GENERATION("openai-image-generation", "openai", TOOL, IMAGE_GEN, "Media", false),
    /** T138 / §X.3.f — OpenAI Responses remote {@code mcp} tool (Streamable HTTP / SSE). */
    OPENAI_MCP("openai-mcp", "openai", TOOL, MCP, "Integrations", false),
    /** T136 / §X.3.d — OpenAI Responses {@code computer_use}. Wired via the
     *  multi-turn screenshot/action loop driving a pluggable
     *  {@code TurComputerUseDriver} (Turing ships a no-op driver, so it runs
     *  only when a real driver bean is configured). Owns the whole turn. */
    OPENAI_COMPUTER_USE("openai-computer-use", "openai", TOOL, COMPUTER_USE, "Automation", true),

    /** T139 / §X.4.a — Anthropic Messages {@code web_search_20250305} server tool. */
    ANTHROPIC_WEB_SEARCH("anthropic-web-search", "anthropic", TOOL, WEB_SEARCH, "Web", false),
    /** T140 / §X.4.b — Anthropic Messages {@code web_fetch_20250910} server tool
     *  (beta {@code web-fetch-2025-09-10}). */
    ANTHROPIC_WEB_FETCH("anthropic-web-fetch", "anthropic", TOOL, WEB_FETCH, "Web", false),
    /** T141 / §X.4.c — Anthropic Messages {@code code_execution_20250522} server
     *  tool (beta {@code code-execution-2025-05-22}); free when paired with
     *  web_search / web_fetch. */
    ANTHROPIC_CODE_EXECUTION("anthropic-code-execution", "anthropic", TOOL, CODE_EXEC, "Code", false),
    /** T143 / §X.4.e — Anthropic Messages {@code computer_use_20251124} tool
     *  (beta {@code computer-use-2025-01-24}). Wired via the same multi-turn
     *  screenshot/action loop + pluggable {@code TurComputerUseDriver} seam as
     *  the OpenAI variant (T136); runs only when a real driver bean is
     *  configured. Anthropic ships a server-side prompt-injection classifier for
     *  this tool. Owns the whole turn. */
    ANTHROPIC_COMPUTER_USE("anthropic-computer-use", "anthropic", TOOL, COMPUTER_USE, "Automation", true),
    /** T142 / §X.4.d — Anthropic {@code text_editor_20250728} client tool, executed
     *  against {@code TurAgentWorkspace} for agentic markdown authoring. */
    ANTHROPIC_TEXT_EDITOR("anthropic-text-editor", "anthropic", TOOL, TEXT_EDITOR, "Authoring", false),
    /** T142 / §X.4.d — Anthropic {@code bash_20250124} client tool, executed via the
     *  pluggable {@code TurAgentBashExecutor} seam (no-op default). */
    ANTHROPIC_BASH("anthropic-bash", "anthropic", TOOL, BASH, "Code", false),
    /** T163 / §X.9.a — Anthropic {@code memory_20250818} client tool (beta
     *  {@code context-management-2025-06-27}). The six file commands
     *  ({@code view}/{@code create}/{@code str_replace}/{@code insert}/
     *  {@code delete}/{@code rename}) are executed against the per-conversation
     *  {@code TurAgentWorkspace} under the {@code memory/} subpath, so Claude
     *  keeps a durable scratchpad across turns. Client-executed multi-turn loop —
     *  owns the turn like the editor tool. */
    ANTHROPIC_MEMORY("anthropic-memory", "anthropic", TOOL, MEMORY, "Memory", false),
    /** T144 / §X.5.a — Anthropic MCP Connector (beta {@code mcp-client-2025-11-20}).
     *  Wired as the {@code mcp_servers} request parameter on the Messages API, so
     *  Claude calls any remote MCP server with no client-side tool code — the
     *  Anthropic analog of the OpenAI remote {@code mcp} tool ({@link #OPENAI_MCP}).
     *  Per-instance {@code configJson} carries {@code serverUrl} / {@code serverLabel}
     *  (server name) plus optional {@code authToken} and {@code allowedTools}. */
    ANTHROPIC_MCP("anthropic-mcp", "anthropic", TOOL, MCP, "Integrations", false),

    /** T490 / §X.19 — Gemini {@code google_search} grounding tool (the Gemini
     *  analog of OpenAI {@code web_search} / Anthropic {@code web_search}). The
     *  model runs Google Search in Google's infrastructure and returns grounding
     *  metadata (per-segment support + source spans) that decodes onto the
     *  shipped {@code TurChatCitation} / {@code type:"citations"} SSE contract
     *  (T152–T155); Google's terms require rendering the returned Search
     *  Suggestion chips, streamed as a {@code type:"searchSuggestions"} event. */
    GEMINI_GOOGLE_SEARCH("gemini-google-search", "gemini", TOOL, WEB_SEARCH, "Web", false),
    /** T491 / §X.19 — Gemini {@code url_context} tool (the Gemini analog of
     *  Anthropic {@code web_fetch}, T140): the model pulls a URL/PDF named in the
     *  prompt directly in Google's infrastructure, no Turing crawler. Coexists
     *  with {@link #GEMINI_GOOGLE_SEARCH} in the same turn. */
    GEMINI_URL_CONTEXT("gemini-url-context", "gemini", TOOL, WEB_FETCH, "Web", false),
    /** T492 / §X.19 — Gemini {@code code_execution} tool: a built-in Python
     *  sandbox (the Gemini analog of OpenAI {@code code_interpreter} (T135) /
     *  Anthropic {@code code_execution} (T141)). The generated code, its output
     *  and inline matplotlib images come back as response parts that
     *  {@code TurGeminiCodeExecutionDecoder} renders into the answer markdown
     *  (images as inline {@code data:} URIs). */
    GEMINI_CODE_EXECUTION("gemini-code-execution", "gemini", TOOL, CODE_EXEC, "Code", false),
    /** T493 / §X.19 — Gemini native image generation (the Gemini analog of
     *  OpenAI {@code image_generation}, T137). Not a {@code tools} entry: it sets
     *  the request's {@code responseModalities} to include {@code IMAGE}, so an
     *  image-capable model ({@code gemini-2.5-flash-image} / Imagen) returns the
     *  generated image as an inline data part, rendered into the answer as a
     *  {@code data:} URI by {@code TurGeminiCodeExecutionDecoder}. */
    GEMINI_IMAGE_GENERATION("gemini-image-generation", "gemini", TOOL, IMAGE_GEN, "Media", false),
    /** T494 / §X.19 — Gemini Computer Use ({@code gemini-2.5-computer-use-*}). The
     *  multi-turn screenshot/action loop driving the same vendor-neutral
     *  {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver}
     *  seam (T136) as the OpenAI/Anthropic variants; ships against the no-op
     *  driver, so it's matrix-selectable without bundling a browser. Owns the
     *  whole turn. */
    GEMINI_COMPUTER_USE("gemini-computer-use", "gemini", TOOL, COMPUTER_USE, "Automation", true),

    /** T147 / §X.6.a — OpenAI Realtime voice ({@code gpt-realtime} family). Unlike
     *  the other capabilities this is <b>not</b> wired into the text-chat
     *  {@code TurNativeChatExecutor} turn: a voice session is minted out-of-band
     *  by {@code TurOpenAiRealtimeVoiceProvider} (server-side ephemeral token) and
     *  the browser then holds the WebSocket transport to {@code gpt-realtime}
     *  directly. The capability exists so the session endpoint (T148) can gate on
     *  it and the picker can surface it. Owns the whole turn — a voice session is
     *  the conversation. */
    OPENAI_REALTIME_VOICE("openai-realtime-voice", "openai", TOOL, VOICE, "Voice", true);

    private final String key;
    private final String pluginType;
    private final TurCapabilityKind kind;
    private final String function;
    private final String category;
    private final boolean ownsTurn;

    TurNativeCapability(String key, String pluginType, TurCapabilityKind kind, String function,
            String category, boolean ownsTurn) {
        this.key = key;
        this.pluginType = pluginType;
        this.kind = kind;
        this.function = function;
        this.category = category;
        this.ownsTurn = ownsTurn;
    }

    public String getKey() {
        return key;
    }

    /** Lowercased provider plugin type (matches {@code TurGenAiLlmProvider#getPluginType()}). */
    public String getPluginType() {
        return pluginType;
    }

    /** T432 — TOOL / REQUEST_OPTION / PLATFORM; decides the UI surface. */
    public TurCapabilityKind getKind() {
        return kind;
    }

    /** T432 — abstract function key; capabilities sharing it are mutually exclusive in the picker. */
    public String getFunction() {
        return function;
    }

    /** T432 — coarse category for visual grouping. */
    public String getCategory() {
        return category;
    }

    /** T432 — when true the capability takes the whole turn (computer_use). */
    public boolean isOwnsTurn() {
        return ownsTurn;
    }

    /** T432 — the executing provider, derived from {@link #getPluginType()}. */
    public TurCapabilityProvider getProvider() {
        return TurCapabilityProvider.fromPluginType(pluginType);
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
