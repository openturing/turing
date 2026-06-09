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
package com.viglet.turing.resilience.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.resilience.TurResilienceRegistry.Kind;

import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Flux;

/**
 * Wraps a Spring AI {@link ChatModel} with retry + circuit breaker + time limiter
 * for {@code call(Prompt)}, and circuit breaker only for {@code stream(Prompt)}.
 *
 * <p>Streaming intentionally skips retry (replay would re-emit duplicate tokens
 * to SSE consumers) and time limiter (streams are long-running by design).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurResilientChatModel implements ChatModel {

    private final ChatModel delegate;
    private final TurResilienceExecutor executor;
    private final TurResilienceRegistry registry;
    private final TurLlmObservation observation;
    private final String providerType;

    public TurResilientChatModel(ChatModel delegate, TurResilienceExecutor executor,
            TurResilienceRegistry registry, TurLlmObservation observation, String providerType) {
        this.delegate = delegate;
        this.executor = executor;
        this.registry = registry;
        this.observation = observation;
        this.providerType = providerType;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // Outer = observability span/timer; inner = resilience pipeline. Order matters:
        // we want the span to capture the full retry/timeout window, not just the last attempt.
        return observation.observeCall(providerType, TurMeterNames.OP_CHAT,
                () -> executor.execute(Kind.LLM, providerType, () -> delegate.call(prompt)));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Flux<ChatResponse> source = delegate.stream(prompt);
        if (!registry.isEnabled(Kind.LLM)) {
            return source;
        }
        return source.transformDeferred(CircuitBreakerOperator.of(registry.circuitBreaker(Kind.LLM, providerType)));
    }

    @Override
    public ChatOptions getOptions() {
        // Spring AI 2.0.0-RC1 deprecated getDefaultOptions() in favour of
        // getOptions(); delegate through the non-deprecated method.
        return delegate.getOptions();
    }
}
