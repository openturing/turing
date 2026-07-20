/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * T740 / §XLIX — the OpenAI wire-format request/response records for the Governed
 * LLM Gateway (Block AZ). Pure DTOs — no logic — mapping the subset of the OpenAI
 * REST contract that ~95% of clients use (chat completions blocking + streaming,
 * embeddings, model listing). Fields Turing does not honour are still accepted
 * and ignored ({@code @JsonIgnoreProperties(ignoreUnknown = true)}) so a stock
 * OpenAI SDK can talk to Turing without change.
 *
 * <p>Field names deliberately use {@code snake_case} to match the OpenAI wire
 * contract exactly (e.g. {@code max_tokens}, {@code finish_reason},
 * {@code prompt_tokens}); this is the one place camelCase is not used.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurOpenAiWire {

    private TurOpenAiWire() {
    }

    // ---- Chat completions -------------------------------------------------

    /**
     * Inbound {@code POST /v1/chat/completions} body. Only {@code model} and
     * {@code messages} are required; {@code stream} selects SSE; sampling params
     * are best-effort passthrough to the resolved instance's own defaults.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionRequest(
            String model,
            List<WireMessage> messages,
            Boolean stream,
            Double temperature,
            Double top_p,
            Integer max_tokens,
            List<String> stop) {
    }

    /** A single chat message. {@code content} is the plain-text subset (no content-part arrays). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WireMessage(String role, String content) {
    }

    /** Non-streaming {@code chat.completion} response object. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletion(
            String id,
            String object,
            long created,
            String model,
            List<Choice> choices,
            Usage usage) {
    }

    /** One choice inside a non-streaming completion. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Choice(int index, WireMessage message, String finish_reason) {
    }

    /** Streaming {@code chat.completion.chunk} frame (one SSE {@code data:} event). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionChunk(
            String id,
            String object,
            long created,
            String model,
            List<ChunkChoice> choices) {
    }

    /** One choice inside a streaming chunk. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChunkChoice(int index, Delta delta, String finish_reason) {
    }

    /** The incremental delta in a streaming chunk. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delta(String role, String content) {
    }

    /** Token accounting block (mirrors OpenAI's {@code usage}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(long prompt_tokens, long completion_tokens, long total_tokens) {
    }

    // ---- Embeddings -------------------------------------------------------

    /**
     * Inbound {@code POST /v1/embeddings} body. {@code input} is a String or an
     * array of Strings (OpenAI allows both), so it is typed {@code Object} and
     * normalised in the service.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingRequest(String model, Object input) {
    }

    /** {@code list} of embedding vectors response. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingResponse(String object, List<EmbeddingData> data, String model, Usage usage) {
    }

    /** One embedding vector. */
    public record EmbeddingData(String object, int index, float[] embedding) {
    }

    // ---- Models -----------------------------------------------------------

    /** {@code GET /v1/models} response. */
    public record ModelList(String object, List<ModelObject> data) {
    }

    /** One model advertised by the gateway. */
    public record ModelObject(String id, String object, long created, String owned_by) {
    }

    // ---- Errors -----------------------------------------------------------

    /** OpenAI-shaped error envelope: {@code {"error": {"message", "type", "code"}}}. */
    public record ErrorEnvelope(ErrorBody error) {
        public static ErrorEnvelope of(String message, String type, String code) {
            return new ErrorEnvelope(new ErrorBody(message, type, null, code));
        }
    }

    /** The error body inside {@link ErrorEnvelope}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String message, String type, String param, String code) {
    }
}
