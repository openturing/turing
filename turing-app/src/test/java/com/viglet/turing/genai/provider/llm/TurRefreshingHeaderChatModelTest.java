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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * T964 / §LXXIII — a blocking model whose headers expire must not keep sending
 * them.
 *
 * <p>Time is injected rather than slept, so these assert the actual rule (half
 * the TTL) instead of a timing coincidence.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRefreshingHeaderChatModelTest {

    private final AtomicLong now = new AtomicLong(1_000L);
    private final AtomicInteger builds = new AtomicInteger();

    private TurRefreshingHeaderChatModel model(long ttlSeconds) {
        return new TurRefreshingHeaderChatModel(() -> {
            builds.incrementAndGet();
            return new StubChatModel();
        }, Duration.ofSeconds(ttlSeconds), now::get);
    }

    @Test
    void theFirstCallBuildsTheModel() {
        TurRefreshingHeaderChatModel model = model(120);

        model.call(new Prompt("hello"));

        assertThat(builds).hasValue(1);
    }

    /** Rebuilding per call would mean a fresh connection pool per call. */
    @Test
    void callsWithinTheHalfLifeReuseTheSameModel() {
        TurRefreshingHeaderChatModel model = model(120);

        model.call(new Prompt("one"));
        now.addAndGet(59);
        model.call(new Prompt("two"));

        assertThat(builds).hasValue(1);
    }

    /**
     * Half-life, not the full TTL: a header minted with a second left is one the
     * managed side rejects by the time the request lands.
     */
    @Test
    void aCallPastTheHalfLifeRebuilds() {
        TurRefreshingHeaderChatModel model = model(120);

        model.call(new Prompt("one"));
        now.addAndGet(60);
        model.call(new Prompt("two"));

        assertThat(builds).hasValue(2);
    }

    /** The long run this exists for: ten minutes of a 120s assertion. */
    @Test
    void aLongRunRebuildsOncePerHalfLifeAndNoMore() {
        TurRefreshingHeaderChatModel model = model(120);

        for (int i = 0; i < 100; i++) {
            model.call(new Prompt("turn " + i));
            now.addAndGet(6); // 100 calls over 10 minutes
        }

        // Calls land at t=0,6,…,594 and a refresh is due every 60s, so the
        // builds are t=0,60,…,540: ten clients for a hundred calls.
        assertThat(builds).hasValue(10);
    }

    @Test
    void streamingAndOptionsGoThroughTheSameRefresh() {
        TurRefreshingHeaderChatModel model = model(10);

        model.stream(new Prompt("one"));
        now.addAndGet(5);
        model.getOptions();

        assertThat(builds).hasValue(2);
    }

    /** A TTL small enough to floor the half-life still refreshes every call. */
    @Test
    void aDegenerateTtlNeverReusesAStaleModel() {
        TurRefreshingHeaderChatModel model = model(1);

        model.call(new Prompt("one"));
        now.addAndGet(1);
        model.call(new Prompt("two"));

        assertThat(builds).hasValue(2);
    }

    private static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(java.util.List.of());
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().build();
        }
    }
}
