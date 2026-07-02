/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.resilience.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * T518 — failover semantics of {@link TurFallbackChatModel}: CALL fails over on
 * a candidate error, STREAM fails over only before the first token, the served
 * instance is tracked, and an all-down chain rethrows.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurFallbackChatModelTest {

    private static ChatResponse responseOf(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** A fake model whose call/stream behaviour is supplied per-test. */
    private static ChatModel model(java.util.function.Supplier<ChatResponse> onCall,
            java.util.function.Supplier<Flux<ChatResponse>> onStream) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return onCall.get();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return onStream.get();
            }

            @Override
            public ChatOptions getOptions() {
                return null;
            }
        };
    }

    private static final Prompt PROMPT = new Prompt("hi");

    @Test
    void callFailsOverToNextCandidateAndTracksServedInstance() {
        ChatModel failing = model(() -> {
            throw new IllegalStateException("rate limited");
        }, Flux::empty);
        ChatModel ok = model(() -> responseOf("from backup"), Flux::empty);

        TurFallbackChatModel meta = new TurFallbackChatModel(List.of(
                new TurFallbackChatModel.Candidate("primary", failing),
                new TurFallbackChatModel.Candidate("backup", ok)));

        ChatResponse response = meta.call(PROMPT);

        assertThat(response.getResult().getOutput().getText()).isEqualTo("from backup");
        assertThat(meta.lastServedInstanceId()).isEqualTo("backup");
    }

    @Test
    void callRethrowsWhenEveryCandidateFails() {
        ChatModel failing = model(() -> {
            throw new IllegalStateException("down");
        }, Flux::empty);

        TurFallbackChatModel meta = new TurFallbackChatModel(List.of(
                new TurFallbackChatModel.Candidate("a", failing),
                new TurFallbackChatModel.Candidate("b", failing)));

        assertThatThrownBy(() -> meta.call(PROMPT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("down");
    }

    @Test
    void streamFailsOverWhenPrimaryErrorsBeforeFirstToken() {
        ChatModel failing = model(() -> null,
                () -> Flux.error(new IllegalStateException("connect failed")));
        ChatModel ok = model(() -> null, () -> Flux.just(responseOf("hello")));

        TurFallbackChatModel meta = new TurFallbackChatModel(List.of(
                new TurFallbackChatModel.Candidate("primary", failing),
                new TurFallbackChatModel.Candidate("backup", ok)));

        List<String> emitted = meta.stream(PROMPT)
                .map(r -> r.getResult().getOutput().getText())
                .collectList().block();

        assertThat(emitted).containsExactly("hello");
        assertThat(meta.lastServedInstanceId()).isEqualTo("backup");
    }

    @Test
    void streamDoesNotFailOverAfterTokensWereEmitted() {
        // Primary emits one token then errors — must NOT switch to backup
        // (that would duplicate already-streamed text).
        ChatModel partialThenError = model(() -> null,
                () -> Flux.concat(Flux.just(responseOf("par")),
                        Flux.error(new IllegalStateException("mid-stream drop"))));
        ChatModel ok = model(() -> null, () -> Flux.just(responseOf("WHOLE")));

        TurFallbackChatModel meta = new TurFallbackChatModel(List.of(
                new TurFallbackChatModel.Candidate("primary", partialThenError),
                new TurFallbackChatModel.Candidate("backup", ok)));

        List<String> emitted = new ArrayList<>();
        assertThatThrownBy(() -> meta.stream(PROMPT)
                .doOnNext(r -> emitted.add(r.getResult().getOutput().getText()))
                .blockLast())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mid-stream drop");
        assertThat(emitted).containsExactly("par");
    }

    @Test
    void singleCandidateServesAndTracks() {
        ChatModel ok = model(() -> responseOf("solo"), Flux::empty);
        TurFallbackChatModel meta = new TurFallbackChatModel(
                List.of(new TurFallbackChatModel.Candidate("only", ok)));

        assertThat(meta.call(PROMPT).getResult().getOutput().getText()).isEqualTo("solo");
        assertThat(meta.lastServedInstanceId()).isEqualTo("only");
    }
}
