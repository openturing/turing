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

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * T964 / §LXXIII — keeps a blocking chat model's short-lived request headers
 * from going stale, by rebuilding the model rather than the header.
 *
 * <p>The managed Viglet AI endpoint requires the T960 install assertion on every
 * call, and that assertion expires in a couple of minutes. On the streaming
 * transport this is easy — {@link TurOpenAiStreamingChatModel} mints one per
 * request. On the blocking transport it is not: the OpenAI Java SDK bakes its
 * headers into the client at build time and exposes no interceptor, and Spring
 * AI's per-prompt {@code httpHeaders} reach only its legacy RestClient
 * transport, not the SDK-backed one this provider uses.
 *
 * <p>So the header cannot be refreshed in place, and the model is rebuilt
 * instead — but only when the current one is past the <b>half-life</b> of the
 * assertion it holds. A run of a hundred evals over ten minutes rebuilds a
 * handful of times instead of a hundred, which keeps the connection churn of a
 * fresh client bounded while guaranteeing the property that matters: a call
 * never leaves carrying an assertion that has expired.
 *
 * <p>Half-life rather than the full TTL because the request has to survive the
 * flight: a header minted with one second left is a header the managed side
 * rejects on arrival.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurRefreshingHeaderChatModel implements ChatModel {

    private final Supplier<ChatModel> modelFactory;
    private final long refreshAfterSeconds;
    private final LongSupplier clock;

    private ChatModel delegate;
    private long builtAtEpochSeconds;

    /**
     * @param modelFactory   builds a model carrying freshly minted headers
     * @param assertionTtl   how long those headers stay valid
     * @param clock          epoch-second source; injectable so a test can travel
     */
    public TurRefreshingHeaderChatModel(Supplier<ChatModel> modelFactory, Duration assertionTtl,
            LongSupplier clock) {
        this.modelFactory = modelFactory;
        this.refreshAfterSeconds = Math.max(1, assertionTtl.toSeconds() / 2);
        this.clock = clock;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return current().call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return current().stream(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return current().getOptions();
    }

    /**
     * The delegate, rebuilt when its headers are past half-life.
     *
     * <p>Synchronized because one model is shared by whatever concurrency the
     * caller has, and two threads racing here would build two clients and leak
     * one of them. The lock is held only for the rebuild decision and, at most
     * once per half-life, for the build itself.
     */
    private synchronized ChatModel current() {
        long now = clock.getAsLong();
        if (delegate == null || now - builtAtEpochSeconds >= refreshAfterSeconds) {
            delegate = modelFactory.get();
            builtAtEpochSeconds = now;
        }
        return delegate;
    }
}
