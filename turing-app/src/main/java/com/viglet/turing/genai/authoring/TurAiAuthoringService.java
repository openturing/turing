/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.TurChatToolOptions;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicStructuredOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generic AI Authoring orchestrator. Each entity that wants to be authored
 * via chat (Intent, AI Agent, Custom Tool, ...) calls
 * {@link #chat(AiAuthoringRequest, String, ParameterizedTypeReference)} from
 * its own controller, passing:
 * <ul>
 *   <li>the conversation history + the form's current snapshot,</li>
 *   <li>an entity-specific system prompt (persona, schema semantics,
 *       hard constraints),</li>
 *   <li>a {@link ParameterizedTypeReference} pointing at
 *       {@code AiAuthoringResponse<EntityShape>} so Spring AI's
 *       {@code BeanOutputConverter} can drive the LLM into producing
 *       valid, schema-compliant JSON.</li>
 * </ul>
 *
 * <p>The default LLM (per Global Settings) is used; no LLM id needs to be
 * threaded from the frontend.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurAiAuthoringService {

    private final TurGlobalSettingsService globalSettings;
    private final TurLLMInstanceRepository llmRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final com.viglet.turing.genai.TurToolExecutionLoop toolExecutionLoop;
    private final TurOpenAiStructuredOutputsService openAiStructured;
    private final TurAnthropicStructuredOutputsService anthropicStructured;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final JsonMapper SCHEMA_JSON = JsonMapper.builder().build();

    public TurAiAuthoringService(TurGlobalSettingsService globalSettings,
            TurLLMInstanceRepository llmRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            com.viglet.turing.genai.TurToolExecutionLoop toolExecutionLoop,
            TurOpenAiStructuredOutputsService openAiStructured,
            TurAnthropicStructuredOutputsService anthropicStructured) {
        this.globalSettings = globalSettings;
        this.llmRepository = llmRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.toolExecutionLoop = toolExecutionLoop;
        this.openAiStructured = openAiStructured;
        this.anthropicStructured = anthropicStructured;
    }

    /**
     * Run one chat turn against the default LLM and parse the response
     * into a typed {@link AiAuthoringResponse}. Convenience overload for
     * entities that don't expose any tools to the authoring chat.
     */
    public <T> AiAuthoringResponse<T> chat(
            AiAuthoringRequest<T> request,
            String systemPrompt,
            ParameterizedTypeReference<AiAuthoringResponse<T>> responseTypeRef) {
        return chat(request, systemPrompt, responseTypeRef, null);
    }

    /**
     * Run one chat turn against the default LLM and parse the response
     * into a typed {@link AiAuthoringResponse}.
     *
     * @param request          conversation + current form snapshot
     * @param systemPrompt     entity-specific persona + rules
     * @param responseTypeRef  must reify {@code AiAuthoringResponse<T>} —
     *                         construct via
     *                         {@code new ParameterizedTypeReference<AiAuthoringResponse<MyShape>>(){}}
     * @param tools            optional tool callbacks the LLM may call
     *                         during the turn (e.g. icon search, RAG, etc.).
     *                         Pass {@code null} or empty for no tools.
     *                         <strong>Must</strong> already be routed through
     *                         {@code TurToolCallbackPipeline.decorate(...)} so
     *                         {@code .md} description overrides + logging are applied.
     * @return parsed response carrying both the conversational reply and
     *         the new state snapshot
     */
    public <T> AiAuthoringResponse<T> chat(
            AiAuthoringRequest<T> request,
            String systemPrompt,
            ParameterizedTypeReference<AiAuthoringResponse<T>> responseTypeRef,
            ToolCallback[] tools) {

        TurLLMInstance instance = resolveDefaultInstance();
        BeanOutputConverter<AiAuthoringResponse<T>> converter = new BeanOutputConverter<>(responseTypeRef);

        // T426 / §X.11.c — durable structured-output path. For a no-tool turn on
        // an OpenAI/Anthropic default LLM, bind AiAuthoringResponse<T>'s shape to
        // the provider (strict json_schema, T173 / forced tool, T174) so the reply
        // is validated JSON by construction — no more prose-around-JSON / empty-turn
        // failures that the BeanOutputConverter free-text path falls over on. Any
        // miss (tools present, other provider, structured call empty/unparseable)
        // falls through to the legacy converter path below, unchanged.
        if (tools == null || tools.length == 0) {
            AiAuthoringResponse<T> structured = tryStructuredAuthoring(instance, request, systemPrompt,
                    converter);
            if (structured != null) {
                return structured;
            }
        }

        ChatModel chatModel = chatModelFor(instance);

        String stateJson = serializeState(request == null ? null : request.currentState());
        String fullSystem = systemPrompt
                + "\n\nCURRENT FORM STATE (JSON snapshot — may be empty for a brand-new entity):\n"
                + stateJson
                + "\n\n"
                + converter.getFormat();

        List<Message> messages = buildAuthoringMessages(fullSystem, request);

        // Authoring responses bundle a conversational reply with the FULL
        // updated state — easily 4-6k tokens for non-trivial flows. Ceil
        // generously so the JSON closing braces never get truncated.
        //
        // Seed from the provider's own concrete options — Spring AI 2.0.0
        // hard-casts prompt.getOptions() to the provider type, so a generic
        // DefaultToolCallingChatOptions throws ClassCastException. See
        // TurChatToolOptions.
        var optionsBuilder = TurChatToolOptions.builderFrom(chatModel)
                .maxTokens(MAX_OUTPUT_TOKENS);
        if (tools != null && tools.length > 0) {
            // Spring AI 2.0.0-RC1 removed internalToolExecutionEnabled; internal
            // tool execution is now the default once callbacks are registered.
            optionsBuilder.toolCallbacks(tools);
        }
        Prompt prompt = new Prompt(messages, optionsBuilder.build());

        String text;
        try {
            // RC1 moved tool execution out of ChatModel.call(); drive the loop
            // explicitly so authoring tools fire (see TurToolExecutionLoop).
            var aiResponse = toolExecutionLoop.call(chatModel, prompt);
            text = aiResponse.getResult() != null
                    && aiResponse.getResult().getOutput() != null
                    && aiResponse.getResult().getOutput().getText() != null
                            ? aiResponse.getResult().getOutput().getText()
                            : "";
        } catch (Exception e) {
            // A genuine infrastructure/provider failure (network, auth, quota)
            // — surface it so the operator sees the real cause.
            log.error("[AiAuthoring] LLM call failed: {}", e.getMessage(), e);
            throw new IllegalStateException("AI authoring chat failed: " + e.getMessage(), e);
        }

        // Below here the failures are model-quality issues (empty turn, prose
        // instead of JSON). Smaller models are flaky at structured-output-with-
        // tools, so degrade gracefully — keep the form intact and let the user
        // retry — rather than 500 on an interactive authoring chat.
        if (text.isBlank()) {
            log.warn("[AiAuthoring] LLM returned an empty response; returning unchanged-state fallback");
            return fallbackResponse(request, EMPTY_RESPONSE_FALLBACK_MESSAGE);
        }
        try {
            return parseResponse(converter, text);
        } catch (RuntimeException e) {
            log.warn("[AiAuthoring] Could not parse LLM output as the expected JSON ({} chars): {} — "
                    + "surfacing the raw reply with unchanged state",
                    text.length(), truncateForLog(text), e);
            // No recoverable JSON — the model just chatted. Show its reply and
            // keep the current form so nothing is lost.
            return fallbackResponse(request, text);
        }
    }

    private static final String EMPTY_RESPONSE_FALLBACK_MESSAGE =
            "I didn't get a usable response that time. Could you rephrase or try again?";

    /**
     * Builds a graceful fallback when the model output can't be turned into a
     * valid state update: surface {@code message} and echo back the current
     * form snapshot unchanged so the user loses nothing.
     */
    private <T> AiAuthoringResponse<T> fallbackResponse(AiAuthoringRequest<T> request, String message) {
        T state = request == null ? null : request.currentState();
        return new AiAuthoringResponse<>(message, state);
    }

    /**
     * Builds the prompt message list: the system message, then the prior
     * conversation turns (history system messages are dropped — the rebuilt
     * system prompt is the single source of truth), seeding an introductory
     * user turn when there's no user message yet.
     */
    private List<Message> buildAuthoringMessages(String fullSystem, AiAuthoringRequest<?> request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(fullSystem));
        if (request != null && request.messages() != null) {
            for (AiAuthoringMessage m : request.messages()) {
                appendHistoryMessage(messages, m);
            }
        }
        if (messages.size() == 1) {
            // No user message yet — seed an introductory request so the LLM
            // returns the first state snapshot + greeting.
            messages.add(new UserMessage(
                    "Greet the user briefly (in their language when known, "
                            + "otherwise English) and offer to start. "
                            + "Also return the current form state unchanged."));
        }
        return messages;
    }

    /**
     * Appends one history turn to {@code messages}, mapping the role. History
     * system messages are dropped (the rebuilt system prompt wins); null/empty
     * turns are ignored.
     */
    private void appendHistoryMessage(List<Message> messages, AiAuthoringMessage m) {
        if (m == null || m.content() == null) {
            return;
        }
        String role = m.role() == null ? "user" : m.role();
        if ("assistant".equalsIgnoreCase(role)) {
            messages.add(new AssistantMessage(m.content()));
        } else if ("system".equalsIgnoreCase(role)) {
            log.debug("[AiAuthoring] Skipping inline system message from history");
        } else {
            messages.add(new UserMessage(m.content()));
        }
    }

    /**
     * Parses the LLM output into a typed {@link AiAuthoringResponse}, tolerating
     * the common ways a model breaks the "bare JSON only" contract:
     * <ul>
     *   <li>conversational prose before/after the JSON (e.g. "He creado…{…}"),</li>
     *   <li>the object wrapped in a ```json … ``` markdown fence.</li>
     * </ul>
     * Tries the raw text first (the happy path), then a recovered JSON substring.
     * Structured-output-as-guardrail is known to be unreliable on smaller models,
     * so this recovery is the safety net rather than the exception.
     */
    private <T> AiAuthoringResponse<T> parseResponse(
            BeanOutputConverter<AiAuthoringResponse<T>> converter, String text) {
        try {
            return converter.convert(text);
        } catch (RuntimeException primary) {
            String json = extractJsonObject(text);
            if (json != null && !json.equals(text)) {
                log.warn("[AiAuthoring] LLM did not return bare JSON; recovered an embedded object "
                        + "({} chars from {} chars of output)", json.length(), text.length());
                return converter.convert(json);
            }
            throw primary;
        }
    }

    /**
     * Extracts the first balanced top-level JSON object from {@code text},
     * skipping any leading prose or markdown fence. Brace counting is
     * string-aware so braces inside string values don't unbalance the scan.
     * Returns {@code null} when no {@code '{'} is present.
     */
    static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static final int MAX_OUTPUT_TOKENS = 8000;

    private static String truncateForLog(String s) {
        if (s == null) return "<null>";
        int max = 2000;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…[truncated " + (s.length() - max) + " more chars]";
    }

    private TurLLMInstance resolveDefaultInstance() {
        String defaultLlmId = globalSettings.getDefaultLlmId();
        if (!StringUtils.hasText(defaultLlmId)) {
            throw new IllegalStateException("No default LLM configured in Global Settings");
        }
        return llmRepository.findById(defaultLlmId)
                .orElseThrow(() -> new IllegalStateException(
                        "Default LLM instance not found: " + defaultLlmId));
    }

    private ChatModel chatModelFor(TurLLMInstance llmInstance) {
        String key = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        return llmModelFactory.createChatModel(llmInstance, key);
    }

    /**
     * T426 / §X.11.c — try the provider-bound structured-output path. Returns a
     * parsed {@link AiAuthoringResponse} when the default LLM is OpenAI/Anthropic
     * and the call yields schema-valid JSON; returns {@code null} (so the caller
     * falls back to the legacy converter path) for any other provider, an empty
     * provider reply, or JSON that doesn't parse. Never throws — a provider error
     * is logged and degrades to the legacy path.
     */
    private <T> AiAuthoringResponse<T> tryStructuredAuthoring(TurLLMInstance instance,
            AiAuthoringRequest<T> request, String systemPrompt,
            BeanOutputConverter<AiAuthoringResponse<T>> converter) {
        Map<String, Object> schema = parseSchema(converter.getJsonSchema());
        if (schema == null) {
            return null;
        }
        String stateJson = serializeState(request == null ? null : request.currentState());
        String system = systemPrompt
                + "\n\nCURRENT FORM STATE (JSON snapshot — may be empty for a brand-new entity):\n"
                + stateJson;
        String input = flattenConversation(request);
        try {
            // strict:false on OpenAI — the converter-generated schema isn't authored
            // to the strict-mode subset, but json_schema still forces a JSON-object
            // reply (no prose, no fences). Anthropic forces a single tool whose input
            // is the object. First non-empty wins; both empty → not OpenAI/Anthropic.
            Optional<String> json = openAiStructured.complete(instance, system, input,
                    "authoring_response", schema, false);
            if (json.isEmpty()) {
                json = anthropicStructured.complete(instance, system, input, "authoring_response", schema);
            }
            if (json.isEmpty() || json.get().isBlank()) {
                return null;
            }
            return converter.convert(json.get());
        } catch (RuntimeException e) {
            log.warn("[AiAuthoring] Structured-output path failed ({}); falling back to legacy converter path",
                    e.getMessage());
            return null;
        }
    }

    /** Parse the converter's JSON-Schema string into a map; null on failure. */
    private Map<String, Object> parseSchema(String jsonSchema) {
        if (!StringUtils.hasText(jsonSchema)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = SCHEMA_JSON.readValue(jsonSchema, Map.class);
            return map;
        } catch (RuntimeException e) {
            log.warn("[AiAuthoring] Could not parse response JSON schema: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Flatten the authoring conversation into one user input for the one-shot
     * structured call (the form state — the real working memory — already rides
     * in the system prompt). When there is no user turn yet, seed the same
     * introductory instruction the legacy path uses.
     */
    private String flattenConversation(AiAuthoringRequest<?> request) {
        StringBuilder sb = new StringBuilder();
        if (request != null && request.messages() != null) {
            for (AiAuthoringMessage m : request.messages()) {
                if (m == null || m.content() == null) {
                    continue;
                }
                String role = m.role() == null ? "user" : m.role();
                if ("system".equalsIgnoreCase(role)) {
                    continue;
                }
                sb.append("assistant".equalsIgnoreCase(role) ? "Assistant: " : "User: ")
                        .append(m.content()).append("\n\n");
            }
        }
        if (sb.length() == 0) {
            return "Greet the user briefly (in their language when known, otherwise English) "
                    + "and offer to start. Also return the current form state unchanged.";
        }
        return sb.toString().strip();
    }

    private String serializeState(Object state) {
        if (state == null) return "{}";
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.warn("[AiAuthoring] Could not serialize current state: {}", e.getMessage());
            return "{}";
        }
    }
}
