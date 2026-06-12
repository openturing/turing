/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T131 / §X.2 — sibling of {@code TurAgentChatExecutor} that routes a turn
 * through a native provider SDK when the instance has opted into a native
 * capability, and otherwise stands aside so the caller falls back to the Spring
 * AI path verbatim.
 *
 * <p>This is the opt-in seam in action: {@link #tryExecute} returns
 * {@link Optional#empty()} for every instance that has no enabled native
 * capability for its vendor — i.e. <b>all existing agents</b>, so behaviour is
 * unchanged until an admin flips a capability row on. When it does handle the
 * turn it currently uses the agent's base system prompt plus the raw history;
 * fusing the full RAG / persona / flow assembly onto the native path is a
 * follow-up that will reuse the post-T33 collaborators.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurNativeChatExecutor {

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurNativeProviderClient nativeClient;
    private final TurNativeCapabilityService capabilityService;
    private final TurOpenAiResponsesService openAiResponsesService;

    public TurNativeChatExecutor(TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            TurNativeCapabilityService capabilityService,
            TurOpenAiResponsesService openAiResponsesService) {
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.capabilityService = capabilityService;
        this.openAiResponsesService = openAiResponsesService;
    }

    /**
     * Attempt to handle the turn natively.
     *
     * @return the native SSE stream, or {@link Optional#empty()} when this turn
     *         is not a native one (no enabled capability, unsupported vendor, or
     *         no usable native client) — the caller must then delegate to the
     *         Spring AI executor.
     */
    public Optional<Flux<ChatResponse>> tryExecute(TurAIAgent agent, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPromptOverride, String conversationId) {
        String pluginType = resolvePluginType(instance);
        if (pluginType == null || !"openai".equals(pluginType)) {
            // Anthropic native server tools arrive in F.3; other vendors have no native path.
            return Optional.empty();
        }

        List<EnabledCapability> capabilities = capabilityService.enabledFor(instance.getId(), pluginType);
        if (capabilities.isEmpty()) {
            return Optional.empty();
        }

        Optional<OpenAIClient> client = nativeClient.openAi(instance);
        if (client.isEmpty()) {
            return Optional.empty();
        }

        String systemPrompt = StringUtils.hasText(systemPromptOverride)
                ? systemPromptOverride
                : agent.getSystemPrompt();
        log.info("[Native] agent '{}' instance '{}' handled by OpenAI Responses path ({} capability/ies)",
                agent.getTitle(), instance.getId(), capabilities.size());
        return Optional.of(openAiResponsesService.chat(client.get(), instance, history,
                systemPrompt, capabilities));
    }

    private String resolvePluginType(TurLLMInstance instance) {
        try {
            return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            log.debug("[Native] could not resolve provider for instance '{}': {}",
                    instance == null ? null : instance.getId(), e.getMessage());
            return null;
        }
    }
}
