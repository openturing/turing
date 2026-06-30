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
package com.viglet.turing.genai.nativeapi.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import com.google.genai.types.UrlContext;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiGroundingDecoder.GroundingResult;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * T489 / §X.19 — the third native chat branch: Google Gemini via the Google
 * GenAI SDK ({@code com.google.genai.Client}), the sibling of
 * {@code TurOpenAiResponsesService} (F.2) and {@code TurAnthropicMessagesService}
 * (F.3).
 *
 * <p>It takes the same fused prompt the other two native paths receive — the
 * system instruction + the flat conversation history produced by
 * {@link com.viglet.turing.genai.TurChatPromptAssembler} (persona + RAG override
 * + MCP instructions + chat memory) — and streams a turn through
 * {@code generateContentStream}, mapping each chunk's text onto the shared
 * {@link ChatResponse} SSE contract. The streaming SDK call is blocking, so it
 * runs on {@link Schedulers#boundedElastic()} exactly like the OpenAI path.
 *
 * <p>This foundation handles base chat + streaming only. The Gemini server-side
 * built-in tools (Google Search grounding, URL context, code execution, image
 * generation, computer use — T490–T494) and the cost/quality primitives
 * (task-type embeddings, batch, files, context caching — T495–T501) layer on
 * top of this branch as later tasks; they extend {@link #buildConfig} with the
 * matching {@code tools(...)} / config without changing this method's contract.
 * The {@code capabilities} list is threaded through for exactly that reason.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGoogleGenAiNativeService {

    /** Mirrors {@code TurGeminiLlmProvider} so the native path picks the same model. */
    static final String DEFAULT_CHAT_MODEL = "gemini-2.0-flash";

    /** Gemini conversation roles: the user turn and the model (assistant) turn. */
    private static final String ROLE_USER = "user";
    private static final String ROLE_MODEL = "model";
    /** The role on every emitted {@link ChatResponse} (the shared SSE contract). */
    private static final String ASSISTANT = "assistant";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurProviderOptionsParser optionsParser;

    public TurGoogleGenAiNativeService(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /**
     * Stream a Gemini turn. {@code systemPrompt} becomes the
     * {@code systemInstruction}; {@code history} maps to the ordered
     * {@code contents} list (user/model parts). Each non-blank streamed chunk is
     * emitted as a {@code "token"} {@link ChatResponse}.
     *
     * @param capabilities the enabled Gemini native capabilities — unused by the
     *                     base text path, wired by T490+ to add server-side tools.
     */
    public Flux<ChatResponse> chat(Client client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        String model = resolveModelName(instance);
        List<Content> contents = toContents(history);
        GenerateContentConfig config = buildConfig(instance, systemPrompt, capabilities);
        return Flux.<ChatResponse>create(sink -> {
            StringBuilder answer = new StringBuilder();
            GroundingMetadata grounding = null;
            try (ResponseStream<GenerateContentResponse> stream =
                    client.models.generateContentStream(model, contents, config)) {
                for (GenerateContentResponse response : stream) {
                    String text = safeText(response);
                    if (StringUtils.hasText(text)) {
                        answer.append(text);
                        sink.next(new ChatResponse(ASSISTANT, text));
                    }
                    // T490 — the grounding metadata rides the candidate; keep the
                    // latest non-empty one (it arrives on the final aggregated chunk).
                    GroundingMetadata chunkGrounding = extractGrounding(response);
                    if (chunkGrounding != null) {
                        grounding = chunkGrounding;
                    }
                }
                // T490 — decode google_search grounding onto the citation + Search
                // Suggestion contracts once the full answer text is known (segment
                // offsets are byte offsets into the complete answer).
                emitGrounding(sink, grounding, answer.toString());
                sink.complete();
            } catch (RuntimeException e) {
                sink.error(e);
            }
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[Native][Gemini] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** The grounding metadata on the first candidate of a chunk, or {@code null}. */
    private static GroundingMetadata extractGrounding(GenerateContentResponse response) {
        return response.candidates()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .flatMap(Candidate::groundingMetadata)
                .orElse(null);
    }

    /**
     * Emit the decoded grounding as a {@code "citations"} event (reusing the
     * T152–T155 contract) and a {@code "searchSuggestions"} event (Google's
     * mandatory Search Suggestion chips). No-op when there is no grounding.
     */
    private static void emitGrounding(reactor.core.publisher.FluxSink<ChatResponse> sink,
            GroundingMetadata grounding, String answerText) {
        if (grounding == null) {
            return;
        }
        GroundingResult result = TurGeminiGroundingDecoder.decode(grounding, answerText);
        if (!result.citations().isEmpty()) {
            writeEvent(sink, result.citations(), "citations");
        }
        if (!result.suggestions().isEmpty()) {
            writeEvent(sink, result.suggestions(), "searchSuggestions");
        }
    }

    private static void writeEvent(reactor.core.publisher.FluxSink<ChatResponse> sink,
            Object payload, String type) {
        try {
            sink.next(new ChatResponse(ASSISTANT, OBJECT_MAPPER.writeValueAsString(payload), type));
        } catch (JacksonException e) {
            log.warn("[Native][Gemini] could not serialize {} event: {}", type, e.getMessage());
        }
    }

    /**
     * The Gemini model name: the instance's {@code chatModel}/{@code model}
     * provider option, then {@code modelName}, then the shared default.
     */
    String resolveModelName(TurLLMInstance instance) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        String configured = firstNonBlank(
                optionsParser.stringValue(options, "chatModel"),
                optionsParser.stringValue(options, "model"),
                instance.getModelName());
        return StringUtils.hasText(configured) ? configured : DEFAULT_CHAT_MODEL;
    }

    /**
     * Build the generation config from the fused system prompt + per-instance
     * sampling options. T490+ extend this with {@code tools(...)} for the native
     * server-side capabilities.
     */
    GenerateContentConfig buildConfig(TurLLMInstance instance, String systemPrompt,
            List<EnabledCapability> capabilities) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        if (StringUtils.hasText(systemPrompt)) {
            builder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }
        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                instance.getTemperature());
        if (temperature != null) {
            builder.temperature(temperature.floatValue());
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), instance.getTopP());
        if (topP != null) {
            builder.topP(topP.floatValue());
        }
        Integer topK = firstNonNull(optionsParser.intValue(options, "topK"), instance.getTopK());
        if (topK != null) {
            builder.topK(topK.floatValue());
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens);
        }
        List<Tool> tools = buildTools(capabilities);
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        return builder.build();
    }

    /**
     * Map the enabled Gemini native capabilities to the SDK's server-side
     * built-in {@link Tool}s. T490 wires {@code google_search} grounding; later
     * tasks (T491 url_context, T492 code_execution) extend this list.
     */
    private static List<Tool> buildTools(List<EnabledCapability> capabilities) {
        List<Tool> tools = new ArrayList<>();
        if (capabilities == null) {
            return tools;
        }
        boolean googleSearch = capabilities.stream()
                .anyMatch(c -> c.capability() == TurNativeCapability.GEMINI_GOOGLE_SEARCH);
        if (googleSearch) {
            tools.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
        }
        // T491 — url_context lets the model pull a URL/PDF named in the prompt
        // directly (Anthropic web_fetch analog); coexists with google_search.
        boolean urlContext = capabilities.stream()
                .anyMatch(c -> c.capability() == TurNativeCapability.GEMINI_URL_CONTEXT);
        if (urlContext) {
            tools.add(Tool.builder().urlContext(UrlContext.builder().build()).build());
        }
        return tools;
    }

    /**
     * Map the flat chat history to Gemini {@link Content} turns. The assistant
     * role maps to Gemini's {@code "model"} role; everything else is a user turn.
     * Blank turns are skipped.
     */
    static List<Content> toContents(List<ChatMessageItem> history) {
        List<Content> contents = new ArrayList<>();
        if (history == null) {
            return contents;
        }
        for (ChatMessageItem item : history) {
            if (item == null || !StringUtils.hasText(item.content())) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(item.role()) ? ROLE_MODEL : ROLE_USER;
            contents.add(Content.builder()
                    .role(role)
                    .parts(Part.fromText(item.content()))
                    .build());
        }
        return contents;
    }

    /** Defensive {@code text()} read — a chunk with only non-text parts yields "". */
    private static String safeText(GenerateContentResponse response) {
        try {
            return response.text();
        } catch (RuntimeException e) {
            return "";
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
