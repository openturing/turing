/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.resilience;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.viglet.turing.resilience.TurResilienceRegistry.Kind;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a {@link Supplier} with retry + circuit breaker + time limiter. Used by
 * the LLM model decorators and the search engine plugin proxy.
 *
 * <p>If the {@link TurResilienceRegistry kind} is disabled (toggle off),
 * {@link #execute} delegates straight to the supplier with no overhead.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Component
public class TurResilienceExecutor {

    private final TurResilienceRegistry registry;

    public TurResilienceExecutor(TurResilienceRegistry registry) {
        this.registry = registry;
    }

    public <T> T execute(Kind kind, String type, Supplier<T> supplier) {
        if (!registry.isEnabled(kind)) {
            return supplier.get();
        }
        CircuitBreaker circuitBreaker = registry.circuitBreaker(kind, type);
        Retry retry = registry.retry(kind, type);
        TimeLimiter timeLimiter = registry.timeLimiter(kind, type);

        Supplier<CompletionStage<T>> futureSupplier = () -> CompletableFuture.supplyAsync(supplier);

        try {
            return Decorators.ofCompletionStage(futureSupplier)
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry, registry.scheduler())
                    .withTimeLimiter(timeLimiter, registry.scheduler())
                    .get()
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof TimeoutException te) {
                throw new TurResilienceTimeoutException(kind, type, te);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Resilience pipeline failed for " + kind + "." + type, cause);
        }
    }
}
