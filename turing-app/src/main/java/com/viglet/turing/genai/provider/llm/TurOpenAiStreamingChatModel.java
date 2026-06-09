/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Hand-rolled {@link ChatModel} that bypasses Spring AI 2.0.0-M6's
 * SDK-based {@code OpenAiChatModel} for the streaming path. Posts directly
 * to OpenAI's {@code /v1/chat/completions} with {@code stream:true} via
 * Spring's {@link WebClient}, parses {@code data:} SSE lines, and emits
 * one {@link ChatResponse} per delta chunk — token-by-token reaches the
 * subscriber instead of being buffered until the round-trip ends.
 *
 * <h2>Why this exists</h2>
 *
 * Spring AI 2.0.0-M6 migrated its OpenAI integration from the
 * WebClient-based {@code OpenAiApi} to the official OpenAI Java SDK
 * ({@code com.openai:openai-java}). The SDK's
 * {@code StreamResponse<ChatCompletionChunk>} exposes deltas as a
 * {@code java.util.Iterator}; Spring AI wraps that in
 * {@code Flux.fromStream(...)} which drains the iterator before
 * publishing. End result: every delta arrives in a single burst at the
 * tail of the call ({@code first_raw_ms ≈ llm_ms}), defeating
 * token-by-token UX.
 *
 * <p>The original Spring AI 2.0.0-M2 path that streamed correctly is
 * gone from the openai module — {@code OpenAiApi.class} no longer ships
 * with the M6 jar. Until Spring AI restores the WebClient path (or
 * patches the SDK adapter to publish per-chunk), this class fills the
 * gap.
 *
 * <h2>What this DOES NOT do</h2>
 *
 * <ul>
 *   <li><b>Tool calling.</b> {@link #call(Prompt)} throws because the
 *       streaming model has no use case for the blocking shape; STREAM
 *       agents are tool-free by design (documented on
 *       {@link com.viglet.turing.persistence.model.agent.TurAgentChatMode}).
 *       If a caller needs blocking + tools, route through the regular
 *       {@code TurOpenAiLlmProvider#createChatModel(...)} which returns
 *       the SDK-backed {@code OpenAiChatModel}.</li>
 *   <li><b>Function/tool deltas mid-stream.</b> Even if the request
 *       carried tool definitions, this parser ignores
 *       {@code choices[0].delta.tool_calls} and only forwards
 *       {@code choices[0].delta.content}. Mid-stream tool execution is
 *       a separate open problem (see {@code §IV.1.b} in
 *       {@code docs/IMPROVEMENTS.md}).</li>
 *   <li><b>Anthropic / Gemini / Ollama.</b> This class is OpenAI-specific
 *       (request shape + SSE format). Other providers either stream
 *       correctly through Spring AI's M6 adapters or have their own
 *       buffering quirks that need separate handling.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
public class TurOpenAiStreamingChatModel implements ChatModel {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SSE_DONE = "[DONE]";

    private final WebClient webClient;
    private final String modelName;
    private final OpenAiChatOptions defaultOptions;

    public TurOpenAiStreamingChatModel(String baseUrl, String apiKey, String modelName,
            OpenAiChatOptions defaultOptions) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.modelName = modelName;
        this.defaultOptions = defaultOptions;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        throw new UnsupportedOperationException(
                "TurOpenAiStreamingChatModel does not support blocking call() — "
                        + "use TurOpenAiLlmProvider#createChatModel for the blocking SDK path. "
                        + "This model is for token-by-token SSE streaming only.");
    }

    @Override
    public ChatOptions getOptions() {
        // Spring AI 2.0.0-RC1 deprecated getDefaultOptions() in favour of
        // getOptions(); the deprecated method now delegates here by default,
        // so legacy callers still resolve to this value.
        return defaultOptions;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Map<String, Object> body = buildRequestBody(prompt);
        return webClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                // bodyToFlux(String.class) on a text/event-stream response
                // emits each SSE `data:` payload (the part AFTER "data: " and
                // BEFORE the blank-line separator). The "[DONE]" sentinel
                // arrives here as the literal string "[DONE]".
                .bodyToFlux(String.class)
                .takeUntil(SSE_DONE::equals)
                .filter(s -> !SSE_DONE.equals(s) && !s.isBlank())
                .map(this::parseChunk)
                .filter(resp -> resp != null);
    }

    /**
     * Translates the Spring AI {@link Prompt} into the JSON body OpenAI's
     * {@code /chat/completions} endpoint expects. Keeps {@code stream:true}
     * + {@code stream_options.include_usage:true} so the final {@code [DONE]}
     * frame carries token counts for {@code TurTokenUsageService}.
     */
    private Map<String, Object> buildRequestBody(Prompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModelName(prompt));
        body.put("messages", buildMessages(prompt.getInstructions()));
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));

        Double temperature = readTemperature(prompt);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        Double topP = readTopP(prompt);
        if (topP != null) {
            body.put("top_p", topP);
        }
        Integer maxTokens = readMaxTokens(prompt);
        if (maxTokens != null) {
            body.put("max_completion_tokens", maxTokens);
        }
        return body;
    }

    private String resolveModelName(Prompt prompt) {
        if (prompt.getOptions() instanceof OpenAiChatOptions opts && opts.getModel() != null) {
            return opts.getModel();
        }
        return modelName;
    }

    private Double readTemperature(Prompt prompt) {
        if (prompt.getOptions() instanceof OpenAiChatOptions opts && opts.getTemperature() != null) {
            return opts.getTemperature();
        }
        return defaultOptions == null ? null : defaultOptions.getTemperature();
    }

    private Double readTopP(Prompt prompt) {
        if (prompt.getOptions() instanceof OpenAiChatOptions opts && opts.getTopP() != null) {
            return opts.getTopP();
        }
        return defaultOptions == null ? null : defaultOptions.getTopP();
    }

    private Integer readMaxTokens(Prompt prompt) {
        if (prompt.getOptions() instanceof OpenAiChatOptions opts && opts.getMaxTokens() != null) {
            return opts.getMaxTokens();
        }
        return defaultOptions == null ? null : defaultOptions.getMaxTokens();
    }

    private List<Map<String, Object>> buildMessages(List<Message> instructions) {
        List<Map<String, Object>> out = new ArrayList<>(instructions.size());
        for (Message m : instructions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", roleOf(m.getMessageType()));
            entry.put("content", m.getText() == null ? "" : m.getText());
            out.add(entry);
        }
        return out;
    }

    private static String roleOf(MessageType type) {
        if (type == null) {
            return "user";
        }
        return switch (type) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case USER -> "user";
        };
    }

    /**
     * Parses one SSE {@code data:} payload into a Spring AI
     * {@link ChatResponse}. Returns {@code null} for chunks that carry
     * only metadata (no {@code choices[0].delta.content}) so the
     * downstream {@code filter} can drop them; the executor's STREAM
     * branch already filters empty-text chunks.
     *
     * <p>The terminal frame (the one with non-null {@code usage}) is
     * forwarded with an empty-text {@link AssistantMessage} but carries
     * {@link Usage} metadata in the {@link ChatResponseMetadata} so
     * {@code TurTokenUsageService} can record token counts.
     */
    private ChatResponse parseChunk(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            String content = "";
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                JsonNode contentNode = delta.path("content");
                if (contentNode.isTextual()) {
                    content = contentNode.asText();
                }
            }
            JsonNode usage = root.path("usage");
            ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
            if (!usage.isMissingNode() && !usage.isNull()) {
                long promptTokens = usage.path("prompt_tokens").asLong(0);
                long completionTokens = usage.path("completion_tokens").asLong(0);
                long totalTokens = usage.path("total_tokens").asLong(0);
                metadata.usage(new Usage() {
                    @Override
                    public Integer getPromptTokens() {
                        return (int) promptTokens;
                    }

                    @Override
                    public Integer getCompletionTokens() {
                        return (int) completionTokens;
                    }

                    @Override
                    public Integer getTotalTokens() {
                        return (int) totalTokens;
                    }

                    @Override
                    public Object getNativeUsage() {
                        return usage;
                    }
                });
            }
            AssistantMessage assistant = new AssistantMessage(content);
            return new ChatResponse(
                    List.of(new Generation(assistant, ChatGenerationMetadata.NULL)),
                    metadata.build());
        } catch (JacksonException e) {
            // OpenAI very occasionally emits a non-JSON keep-alive line.
            // Drop silently rather than abort the stream — the alternative
            // is a half-completed bubble for the user.
            log.debug("[StreamingOpenAi] dropping unparseable SSE chunk: {}", e.getMessage());
            return null;
        }
    }
}
