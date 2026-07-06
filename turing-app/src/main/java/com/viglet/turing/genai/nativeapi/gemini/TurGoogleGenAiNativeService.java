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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolCodeExecution;
import com.google.genai.types.UrlContext;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory;
import com.viglet.turing.genai.nativeapi.cache.TurContextCacheRef;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.nativeapi.cache.TurContextCacheRequest;
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
    private final TurContextCacheProviderFactory contextCacheFactory;

    public TurGoogleGenAiNativeService(TurProviderOptionsParser optionsParser,
            TurContextCacheProviderFactory contextCacheFactory) {
        this.optionsParser = optionsParser;
        this.contextCacheFactory = contextCacheFactory;
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
            List<EnabledCapability> capabilities, Params calibration) {
        String model = resolveModelName(instance);
        List<Content> contents = toContents(history);
        // T499 / §X.20 — when context caching is opted in, cache the stable prefix
        // (the system instruction) once and thread its handle into the config; the
        // no-op default returns empty for every other vendor, leaving the request
        // byte-for-byte unchanged.
        Optional<TurContextCacheRef> cacheRef = resolveContextCache(instance, model, systemPrompt);
        GenerateContentConfig config = buildConfig(instance, systemPrompt, capabilities, cacheRef, calibration);
        boolean surfaceThoughts = isThinkingSurfaced(instance);
        return Flux.<ChatResponse>create(sink -> {
            StringBuilder answer = new StringBuilder();
            StringBuilder thoughts = new StringBuilder();
            GroundingMetadata grounding = null;
            List<Part> codeParts = new ArrayList<>();
            try (ResponseStream<GenerateContentResponse> stream =
                    client.models.generateContentStream(model, contents, config)) {
                for (GenerateContentResponse response : stream) {
                    // T498 — when thoughts are surfaced, split thought parts from
                    // answer parts (response.text() would conflate them); otherwise
                    // keep the fast aggregate path.
                    if (surfaceThoughts) {
                        streamSplitByThought(response, sink, answer, thoughts);
                    } else {
                        String text = safeText(response);
                        if (StringUtils.hasText(text)) {
                            answer.append(text);
                            sink.next(new ChatResponse(ASSISTANT, text));
                        }
                    }
                    // T490 — the grounding metadata rides the candidate; keep the
                    // latest non-empty one (it arrives on the final aggregated chunk).
                    GroundingMetadata chunkGrounding = extractGrounding(response);
                    if (chunkGrounding != null) {
                        grounding = chunkGrounding;
                    }
                    // T492 — collect code_execution parts (code/output/images) so
                    // they render after the streamed text.
                    collectCodeParts(response, codeParts);
                }
                // T492 — render the collected code/output/images as trailing
                // markdown (emitted as a token event so it joins the answer).
                String codeMarkdown = TurGeminiCodeExecutionDecoder.render(codeParts);
                if (StringUtils.hasText(codeMarkdown)) {
                    sink.next(new ChatResponse(ASSISTANT, codeMarkdown));
                }
                // T498 — surface the accumulated thought summary as the shared
                // T178 "reasoning" event (the "Why this answer" panel).
                if (thoughts.length() > 0) {
                    sink.next(new ChatResponse(ASSISTANT, thoughts.toString(), "reasoning"));
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

    /**
     * T498 — walk a chunk's text parts, routing thought-summary parts
     * ({@code thought == true}) to {@code thoughts} and the rest to the answer
     * (streamed as tokens). Used only when the instance surfaces thoughts.
     */
    private static void streamSplitByThought(GenerateContentResponse response,
            reactor.core.publisher.FluxSink<ChatResponse> sink, StringBuilder answer,
            StringBuilder thoughts) {
        response.candidates()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .ifPresent(parts -> {
                    for (Part part : parts) {
                        String text = part.text().orElse("");
                        if (!StringUtils.hasText(text)) {
                            continue;
                        }
                        if (part.thought().orElse(false)) {
                            thoughts.append(text);
                        } else {
                            answer.append(text);
                            sink.next(new ChatResponse(ASSISTANT, text));
                        }
                    }
                });
    }

    /** Collect the code-execution parts (code/output/image) of a chunk's first candidate. */
    private static void collectCodeParts(GenerateContentResponse response, List<Part> sink) {
        response.candidates()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .flatMap(c -> c.content())
                .flatMap(content -> content.parts())
                .ifPresent(parts -> parts.stream()
                        .filter(TurGeminiCodeExecutionDecoder::isCodeExecutionPart)
                        .forEach(sink::add));
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
     * sampling options (no context cache). Retained for callers/tests that don't
     * exercise caching; delegates to the cache-aware overload with no ref.
     */
    GenerateContentConfig buildConfig(TurLLMInstance instance, String systemPrompt,
            List<EnabledCapability> capabilities) {
        return buildConfig(instance, systemPrompt, capabilities, Optional.empty(), Params.NONE);
    }

    /** Cache-aware overload without persona calibration (retained for callers/tests). */
    GenerateContentConfig buildConfig(TurLLMInstance instance, String systemPrompt,
            List<EnabledCapability> capabilities, Optional<TurContextCacheRef> cacheRef) {
        return buildConfig(instance, systemPrompt, capabilities, cacheRef, Params.NONE);
    }

    /**
     * Build the generation config from the fused system prompt + per-instance
     * sampling options. T490+ extend this with {@code tools(...)} for the native
     * server-side capabilities.
     *
     * <p>T499 / §X.20 — when {@code cacheRef} is present the stable prefix lives
     * in a Gemini {@code cachedContents} object referenced by
     * {@link GenerateContentConfig.Builder#cachedContent(String)}; the Gemini API
     * forbids also sending {@code systemInstruction}/{@code tools} on the request
     * (they belong to the cache), so both are omitted in that case.
     */
    GenerateContentConfig buildConfig(TurLLMInstance instance, String systemPrompt,
            List<EnabledCapability> capabilities, Optional<TurContextCacheRef> cacheRef,
            Params calibration) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        boolean cached = cacheRef != null && cacheRef.isPresent();
        if (cached) {
            builder.cachedContent(cacheRef.get().handle());
        } else if (StringUtils.hasText(systemPrompt)) {
            builder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }
        // T606 — opt-in persona style→model calibration takes precedence over the
        // providerOptionsJson override and the instance default; null means "no
        // calibration", so the legacy precedence is preserved.
        Double temperature = firstNonNull(calibration.temperature(),
                optionsParser.doubleValue(options, "temperature"), instance.getTemperature());
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
        Integer maxTokens = firstNonNull(calibration.maxTokens(),
                optionsParser.intValue(options, "maxTokens"));
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens);
        }
        List<Tool> tools = buildTools(capabilities);
        if (!tools.isEmpty() && cached) {
            // The cache owns systemInstruction + tools; server-side tools can't be
            // re-declared on a cached request. Skip them (logged) this turn.
            log.warn("[Native][Gemini] instance '{}' has context caching active alongside {} "
                    + "server-side tool(s); tools are bypassed this turn (they belong to the cache)",
                    instance.getId(), tools.size());
        } else if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        // T493 — native image generation is not a tool: request the IMAGE
        // response modality (alongside TEXT) so an image-capable model
        // (gemini-2.5-flash-image / Imagen) returns the generated image as an
        // inline data part, rendered into the answer as a data: URI.
        if (hasCapability(capabilities, TurNativeCapability.GEMINI_IMAGE_GENERATION)) {
            builder.responseModalities("TEXT", "IMAGE");
        }
        // T498 — Gemini 2.5 thinking: a thinkingBudget caps reasoning tokens and
        // includeThoughts surfaces the thought summary, which streams back as the
        // shared T178 "reasoning" SSE event ("Why this answer" panel).
        ThinkingConfig thinking = resolveThinkingConfig(options);
        if (thinking != null) {
            builder.thinkingConfig(thinking);
        }
        return builder.build();
    }

    /**
     * T499 / §X.20 — resolve (creating/reusing) the explicit context cache for the
     * instance's stable prefix, or {@link Optional#empty()} when context caching
     * is not opted in, the prefix is blank, or the provider declines/fails
     * (fail-open). Gated by the {@code contextCacheEnabled} provider option, with
     * an optional {@code contextCacheTtlSeconds} lifetime; only the Gemini
     * provider implements a real cache (every other vendor resolves to the no-op).
     */
    Optional<TurContextCacheRef> resolveContextCache(TurLLMInstance instance, String model,
            String systemPrompt) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        if (!isContextCacheEnabled(options) || !StringUtils.hasText(systemPrompt)) {
            return Optional.empty();
        }
        Integer ttlSeconds = optionsParser.intValue(options, "contextCacheTtlSeconds");
        Duration ttl = ttlSeconds != null && ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;
        TurContextCacheRequest request =
                TurContextCacheRequest.ofSystemInstruction(instance, model, systemPrompt, ttl);
        return contextCacheFactory.resolve(instance).ensureCache(request);
    }

    private boolean isContextCacheEnabled(Map<String, Object> options) {
        Object value = options == null ? null : options.get("contextCacheEnabled");
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    /**
     * Resolve the per-instance thinking config, or {@code null} when neither knob
     * is set (unchanged request). {@code thinkingBudget} caps reasoning tokens
     * (0 disables thinking on models that allow it; -1 = dynamic); {@code
     * includeThoughts} asks the model to return its thought summary.
     */
    ThinkingConfig resolveThinkingConfig(Map<String, Object> options) {
        Integer budget = optionsParser.intValue(options, "thinkingBudget");
        boolean includeThoughts = isIncludeThoughts(options);
        if (budget == null && !includeThoughts) {
            return null;
        }
        ThinkingConfig.Builder builder = ThinkingConfig.builder().includeThoughts(includeThoughts);
        if (budget != null) {
            builder.thinkingBudget(budget);
        }
        return builder.build();
    }

    /** True when the instance opted into surfacing Gemini's thought summary. */
    boolean isThinkingSurfaced(TurLLMInstance instance) {
        return isIncludeThoughts(optionsParser.parse(instance.getProviderOptionsJson()));
    }

    private boolean isIncludeThoughts(Map<String, Object> options) {
        Object value = options == null ? null : options.get("includeThoughts");
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    private static boolean hasCapability(List<EnabledCapability> capabilities,
            TurNativeCapability capability) {
        return capabilities != null && capabilities.stream()
                .anyMatch(c -> c.capability() == capability);
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
        if (hasCapability(capabilities, TurNativeCapability.GEMINI_GOOGLE_SEARCH)) {
            tools.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
        }
        // T491 — url_context lets the model pull a URL/PDF named in the prompt
        // directly (Anthropic web_fetch analog); coexists with google_search.
        if (hasCapability(capabilities, TurNativeCapability.GEMINI_URL_CONTEXT)) {
            tools.add(Tool.builder().urlContext(UrlContext.builder().build()).build());
        }
        // T492 — code_execution: a built-in Python sandbox; the generated code,
        // output and inline images return as response parts (rendered by
        // TurGeminiCodeExecutionDecoder).
        if (hasCapability(capabilities, TurNativeCapability.GEMINI_CODE_EXECUTION)) {
            tools.add(Tool.builder().codeExecution(ToolCodeExecution.builder().build()).build());
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
