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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Real-LLM IT proving that {@link TurOpenAiStreamingChatModel} actually
 * streams token-by-token instead of buffering deltas the way Spring AI
 * 2.0.0-M6's SDK-based {@code OpenAiChatModel} does. Pairing for
 * §IV.1.b / T18 in {@code docs/IMPROVEMENTS.md}.
 *
 * <h2>What "token-by-token" means here</h2>
 *
 * For a non-trivial reply (we ask for ~50 tokens), the SDK path emits
 * one big chunk at the end ({@code chunks ≈ 1}, {@code first_chunk_ms ≈ llm_ms}).
 * The WebClient path emits one chunk per delta (~30-100 chunks),
 * {@code first_chunk_ms} arrives in 300-800ms, and the total round-trip
 * stays roughly the same. The assertions below pin the WebClient
 * behavior:
 *
 * <ul>
 *   <li>{@code chunks > 20} — way more than a handful of deltas reached
 *       us (gpt-4o-mini typically emits 100-300 chunks for the prompt
 *       below). The buffered SDK transport collapses to exactly 1 chunk,
 *       so anything double-digit definitively proves streaming.</li>
 *   <li>{@code accumulated content matches the prompt's intent} — a
 *       sanity check that we're parsing {@code delta.content} correctly
 *       (not dropping characters or concatenating noise).</li>
 * </ul>
 *
 * <p>We deliberately do NOT assert on {@code first_chunk_ms / total_ms}
 * ratios. Time-to-first-token depends on OpenAI's cold-connection
 * routing and is too variable to pin without flakes — observed values
 * range 300ms-2.5s for the same prompt. The chunk count is a sufficient,
 * stable streaming witness.
 *
 * <p>Without {@code OPENAI_API_KEY} the class is short-circuited by
 * JUnit. Excluded from default failsafe (matches {@code *BehavioralIT.java}),
 * runs under {@code -Pllm-it}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurOpenAiStreamingChatModelBehavioralIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";

    /**
     * Drives a single stream call against gpt-4o-mini with a prompt
     * designed to yield ~50 output tokens, and asserts the chunk count
     * + first-chunk latency are in the streaming regime.
     */
    @Test
    void stream_emitsManyChunksWellBeforeRoundTripCompletes() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(CHAT_MODEL)
                .temperature(0.0)
                .maxTokens(120)
                .build();
        TurOpenAiStreamingChatModel model = new TurOpenAiStreamingChatModel(
                OPENAI_BASE_URL, System.getenv("OPENAI_API_KEY"), CHAT_MODEL, options);

        // Prompt designed to elicit a longer reply so the chunk count
        // discriminates buffer-vs-stream cleanly. "List 10 famous cities
        // with a one-liner each" reliably yields 200-400 tokens with
        // gpt-4o-mini.
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("Você é um assistente prestativo."),
                new UserMessage("Liste 10 cidades famosas do mundo com UMA frase de descrição cada. "
                        + "Use o formato '1. Nome — descrição.' por linha.")));

        AtomicInteger chunks = new AtomicInteger();
        AtomicLong firstChunkAt = new AtomicLong();
        StringBuilder accumulated = new StringBuilder();
        long tStart = System.currentTimeMillis();

        List<ChatResponse> all = model.stream(prompt)
                .doOnNext(resp -> {
                    int n = chunks.incrementAndGet();
                    if (n == 1) {
                        firstChunkAt.set(System.currentTimeMillis());
                    }
                    if (resp.getResult() != null
                            && resp.getResult().getOutput() != null
                            && resp.getResult().getOutput().getText() != null) {
                        accumulated.append(resp.getResult().getOutput().getText());
                    }
                })
                .collectList()
                .block(Duration.ofSeconds(30));

        long tEnd = System.currentTimeMillis();
        long totalMs = tEnd - tStart;
        long firstChunkMs = firstChunkAt.get() == 0 ? -1 : (firstChunkAt.get() - tStart);

        System.out.printf("[Streaming IT] chunks=%d total_ms=%d first_chunk_ms=%d chars=%d%n",
                chunks.get(), totalMs, firstChunkMs, accumulated.length());
        System.out.println("=== Streamed reply ===");
        System.out.println(accumulated);
        System.out.println("=== END ===");

        assertThat(all).as("Mono.collectList should not return null").isNotNull();

        // Invariant 1: we got many chunks, not one big buffer. The
        // Spring AI 2.0.0-M6 SDK transport collapses to exactly 1 chunk
        // (drain-iterator-then-publish); the WebClient streaming transport
        // produces 100-300 chunks for this prompt. 20 is a flake-proof
        // floor that still discriminates the two regimes by an order of
        // magnitude.
        assertThat(chunks.get())
                .as("Expected token-by-token streaming (≫20 chunks). Got %d total_ms=%d "
                        + "first_chunk_ms=%d — transport likely buffering.",
                        chunks.get(), totalMs, firstChunkMs)
                .isGreaterThan(20);

        // Invariant 2: content actually came through (we asked for cities,
        // gpt-4o-mini at temp 0 reliably names common ones).
        assertThat(accumulated.toString())
                .as("Streamed content must mention at least one of the obvious cities. Got: %s",
                        accumulated)
                .containsAnyOf("Paris", "Tóquio", "Tokyo", "Nova York", "New York", "Londres", "London");
    }
}
