/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.List;
import java.util.OptionalLong;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.f — exact pre-flight prompt-token count for Anthropic instances via
 * the native {@code /v1/messages/count_tokens} endpoint, replacing the
 * {@code chars/4} {@code cl100k_base} heuristic ({@link TurTokenBudgetService})
 * with the vendor's exact number where available.
 *
 * <p>Only Anthropic exposes a token-count endpoint, so {@link #countExact} is
 * empty for every other vendor; {@link #count} then falls back to the heuristic.
 * To avoid role-alternation constraints the whole prompt is collapsed into one
 * system + one user message before counting — the token total is essentially
 * unchanged and a budget decision ("16k ± a few") is unaffected.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurTokenCountingService {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    private final TurNativeProviderClient nativeProviderClient;

    public TurTokenCountingService(TurNativeProviderClient nativeProviderClient) {
        this.nativeProviderClient = nativeProviderClient;
    }

    /** True when this instance supports an exact token count (Anthropic only). */
    public boolean isExactAvailable(TurLLMInstance instance) {
        return instance != null && nativeProviderClient.anthropic(instance).isPresent();
    }

    /**
     * The vendor's exact input-token count for the prompt, or empty when the
     * instance has no count endpoint or the call fails (caller falls back).
     */
    public OptionalLong countExact(TurLLMInstance instance, List<Message> messages) {
        if (instance == null || messages == null || messages.isEmpty()) {
            return OptionalLong.empty();
        }
        var client = nativeProviderClient.anthropic(instance);
        if (client.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(countAnthropic(client.get(), instance, messages));
        } catch (Exception e) {
            log.warn("[TokenCount] Anthropic count_tokens failed for instance {} — falling back: {}",
                    instance.getId(), e.getMessage());
            return OptionalLong.empty();
        }
    }

    /** Exact count when available, else the {@code chars/4} heuristic. */
    public int count(TurLLMInstance instance, List<Message> messages) {
        OptionalLong exact = countExact(instance, messages);
        if (exact.isPresent()) {
            return (int) Math.min(Integer.MAX_VALUE, exact.getAsLong());
        }
        return TurTokenBudgetService.estimateTokens(messages);
    }

    private long countAnthropic(AnthropicClient client, TurLLMInstance instance,
            List<Message> messages) {
        StringBuilder system = new StringBuilder();
        StringBuilder user = new StringBuilder();
        for (Message m : messages) {
            String text = m.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (m.getMessageType() == MessageType.SYSTEM) {
                system.append(text).append('\n');
            } else {
                user.append(text).append('\n');
            }
        }
        MessageCountTokensParams.Builder builder = MessageCountTokensParams.builder()
                .model(resolveModel(instance))
                .addUserMessage(user.isEmpty() ? " " : user.toString());
        if (!system.isEmpty()) {
            builder.system(system.toString());
        }
        return client.messages().countTokens(builder.build()).inputTokens();
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }
}
