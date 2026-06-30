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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import reactor.core.publisher.Flux;

/**
 * T518 / §XXVIII.14 — boots the full Spring context to assert the cost-aware
 * meta-provider is wired and <strong>inert by default</strong>: with no fallback
 * chain configured, {@code wrapWithFallback} returns the primary model
 * unchanged (legacy single-model path), and a chain of unresolvable ids still
 * fails safe to the primary. Also checks the chain/mode config round-trips.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurMetaChatModelResolverIT extends AbstractTuringSpringIT {

    @Autowired
    private TurMetaChatModelResolver metaChatModelResolver;

    @Autowired
    private TurGlobalSettingsService globalSettingsService;

    private static ChatModel dummyModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return null;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.empty();
            }

            @Override
            public ChatOptions getOptions() {
                return null;
            }
        };
    }

    private static TurLLMInstance instance(String id) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(id);
        instance.setEnabled(1);
        return instance;
    }

    @Test
    void emptyChainReturnsPrimaryModelUnchanged() {
        assertThat(globalSettingsService.getLlmFallbackChainIds()).isEmpty();
        assertThat(globalSettingsService.getLlmFallbackMode())
                .isEqualTo(TurLlmFallbackMode.PRIORITY);

        ChatModel primary = dummyModel();
        ChatModel wrapped = metaChatModelResolver.wrapWithFallback(instance("p1"), primary);

        assertThat(wrapped).isSameAs(primary);
    }

    @Test
    void chainOfUnresolvableIdsFailsSafeToPrimary() {
        globalSettingsService.updateLlmFallbackChainIds(List.of("no-such-a", "no-such-b"));
        try {
            ChatModel primary = dummyModel();
            ChatModel wrapped = metaChatModelResolver.wrapWithFallback(instance("p1"), primary);
            // No chain id resolves to an enabled instance → only the primary
            // candidate survives → the primary model is returned unchanged.
            assertThat(wrapped).isSameAs(primary);
        } finally {
            globalSettingsService.updateLlmFallbackChainIds(List.of());
        }
    }

    @Test
    void fallbackModeRoundTrips() {
        globalSettingsService.updateLlmFallbackMode(TurLlmFallbackMode.CHEAPEST);
        try {
            assertThat(globalSettingsService.getLlmFallbackMode())
                    .isEqualTo(TurLlmFallbackMode.CHEAPEST);
        } finally {
            globalSettingsService.updateLlmFallbackMode(TurLlmFallbackMode.PRIORITY);
        }
    }
}
