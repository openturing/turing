/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class TurNativeChatExecutorTest {

    @Mock private TurGenAiLlmProviderFactory providerFactory;
    @Mock private TurNativeProviderClient nativeClient;
    @Mock private TurNativeCapabilityService capabilityService;
    @Mock private TurOpenAiResponsesService openAiResponsesService;
    @Mock private TurGenAiLlmProvider provider;
    @Mock private OpenAIClient openAiClient;

    private TurNativeChatExecutor executor() {
        return new TurNativeChatExecutor(providerFactory, nativeClient, capabilityService,
                openAiResponsesService);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setTitle("OpenAI");
        return instance;
    }

    private static TurAIAgent agent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Agent");
        agent.setSystemPrompt("You are helpful.");
        return agent;
    }

    private final List<ChatMessageItem> history = List.of(new ChatMessageItem("user", "hello"));

    @Test
    void emptyWhenVendorIsNotOpenAi() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void emptyWhenNoCapabilityEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void emptyWhenNativeClientUnavailable() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai"))
                .thenReturn(List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null)));
        when(nativeClient.openAi(instance)).thenReturn(Optional.empty());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void routesToResponsesServiceWhenOpenAiAndCapabilityEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null));
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(caps);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), eq(history),
                any(), anyList()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        List<ChatResponse> emitted = result.get().collectList().block();
        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).content()).isEqualTo("hi");
    }

    @Test
    void emptyWhenProviderResolutionFails() {
        TurLLMInstance instance = instance();
        lenient().when(providerFactory.getProvider(instance))
                .thenThrow(new IllegalStateException("no vendor"));

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }
}
