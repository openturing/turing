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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T518 / §XXVIII.14 — an OpenRouter-style meta {@link ChatModel} over an ordered
 * list of candidate models (each already resilience-wrapped via
 * {@link TurLlmModelFactory}). It serves the first candidate and, when that
 * candidate <em>exhausts</em> its own retry/circuit-breaker pipeline and still
 * fails (provider error, rate-limit, timeout), <strong>fails over</strong> to
 * the next candidate, and so on — only throwing when every candidate is down.
 *
 * <p>The candidate order encodes the routing policy
 * ({@link TurLlmFallbackMode}): {@code PRIORITY} = configured order,
 * {@code CHEAPEST} = price-ascending, both resolved by the caller before
 * constructing this model.
 *
 * <p><b>Streaming failover is first-token-safe.</b> A streaming candidate is
 * only abandoned for the next when it errors <em>before emitting any token</em>;
 * once tokens are on the wire, an error propagates (replaying onto a different
 * vendor would duplicate text to the SSE consumer).
 *
 * <p>Tracks the instance that actually served the last call so cost can be
 * attributed to the real vendor (per-tenant cost attribution, F.13).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurFallbackChatModel implements ChatModel {

    /** One link in the chain: the instance id (for cost attribution) + its model. */
    public record Candidate(String instanceId, ChatModel model) {
    }

    private final List<Candidate> candidates;
    private final AtomicReference<String> lastServedInstanceId = new AtomicReference<>();

    public TurFallbackChatModel(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("TurFallbackChatModel requires at least one candidate");
        }
        this.candidates = List.copyOf(candidates);
    }

    /** The instance id that served the most recent successful call, for cost attribution. */
    public String lastServedInstanceId() {
        return lastServedInstanceId.get();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RuntimeException lastError = null;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            try {
                ChatResponse response = candidate.model().call(prompt);
                lastServedInstanceId.set(candidate.instanceId());
                if (i > 0) {
                    log.info("[Meta] failover succeeded on candidate #{} (instance {})",
                            i, candidate.instanceId());
                }
                return response;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("[Meta] candidate #{} (instance {}) failed: {} — {}",
                        i, candidate.instanceId(), e.getMessage(),
                        i + 1 < candidates.size() ? "failing over" : "no more candidates");
            }
        }
        throw lastError;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return streamFrom(prompt, 0);
    }

    /**
     * Streams candidate {@code index}; on an error <em>before the first token</em>
     * resumes with the next candidate, otherwise propagates (no mid-stream
     * failover — it would duplicate already-emitted tokens).
     */
    private Flux<ChatResponse> streamFrom(Prompt prompt, int index) {
        Candidate candidate = candidates.get(index);
        AtomicBoolean emitted = new AtomicBoolean(false);
        return candidate.model().stream(prompt)
                .doOnNext(r -> {
                    if (emitted.compareAndSet(false, true)) {
                        lastServedInstanceId.set(candidate.instanceId());
                    }
                })
                .onErrorResume(err -> {
                    if (emitted.get() || index + 1 >= candidates.size()) {
                        return Flux.error(err);
                    }
                    log.warn("[Meta] stream candidate #{} (instance {}) failed before first token: {} — failing over",
                            index, candidate.instanceId(),
                            err instanceof Exception ex ? ex.getMessage() : String.valueOf(err));
                    return streamFrom(prompt, index + 1);
                });
    }

    @Override
    public ChatOptions getOptions() {
        // Primary candidate's options drive prompt assembly; failover candidates
        // inherit the same Prompt (model-agnostic messages).
        return candidates.get(0).model().getOptions();
    }
}
