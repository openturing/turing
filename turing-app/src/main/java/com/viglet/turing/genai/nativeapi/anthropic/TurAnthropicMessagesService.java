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
package com.viglet.turing.genai.nativeapi.anthropic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.WebFetchTool20250910;
import com.anthropic.models.messages.WebSearchTool20250305;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * F.3 / §X.4 — runs a chat turn through the Anthropic <b>Messages API</b>
 * ({@code /v1/messages}) with server-side built-in tools, the Anthropic analog
 * of {@link com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService}.
 *
 * <p>Each enabled {@link TurNativeCapability} keyed to the {@code anthropic}
 * vendor maps to one server-side tool that executes inside Anthropic's
 * infrastructure — Turing writes <em>zero</em> Java tool callbacks for any of
 * them, and the answer comes back in a single turn (CALL semantics):
 * <ul>
 *   <li>{@code ANTHROPIC_WEB_SEARCH} → {@code web_search_20250305} (T139)</li>
 *   <li>{@code ANTHROPIC_WEB_FETCH}  → {@code web_fetch_20250910} (T140, beta)</li>
 * </ul>
 *
 * <p>Tools that ship as Anthropic beta features (web_fetch, code_execution)
 * also require their {@code anthropic-beta} header; {@link #buildTools} collects
 * the required beta tokens into the supplied set so {@link #buildParams} can
 * attach the header. web_search is GA and needs no beta token.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAnthropicMessagesService {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final long DEFAULT_MAX_TOKENS = 4096L;

    /** {@code anthropic-beta} token gating the web_fetch server tool (T140). */
    private static final String BETA_WEB_FETCH = "web-fetch-2025-09-10";

    private final TurProviderOptionsParser optionsParser;

    public TurAnthropicMessagesService(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /**
     * Execute the turn and emit the assistant text as a single SSE token event
     * (CALL semantics — the server-side-tool turn must complete before the
     * answer is known).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        MessageCreateParams params = buildParams(instance, history, systemPrompt, capabilities);
        return Mono.fromCallable(() -> {
                    long t0 = System.currentTimeMillis();
                    Message response = client.messages().create(params);
                    String text = extractText(response);
                    log.info("[Native][Anthropic-Messages] instance '{}' model '{}' tools {} -> {} chars in {} ms",
                            instance.getId(), resolveModel(instance),
                            capabilities.stream().map(c -> c.capability().getKey()).toList(),
                            text.length(), System.currentTimeMillis() - t0);
                    return text;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][Anthropic-Messages] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    MessageCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(instance))
                .maxTokens(resolveMaxTokens(instance));

        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }

        if (history != null) {
            for (ChatMessageItem message : history) {
                if (message == null || !StringUtils.hasText(message.content())) {
                    continue;
                }
                if (isAssistant(message.role())) {
                    builder.addAssistantMessage(message.content());
                } else {
                    builder.addUserMessage(message.content());
                }
            }
        }

        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
        }

        Set<String> betas = new LinkedHashSet<>();
        for (ToolUnion tool : buildTools(capabilities, betas)) {
            builder.addTool(tool);
        }
        if (!betas.isEmpty()) {
            builder.putAdditionalHeader("anthropic-beta", String.join(",", betas));
        }
        return builder.build();
    }

    /** Convenience overload that discards the beta-token set (tests / web_search-only). */
    List<ToolUnion> buildTools(List<EnabledCapability> capabilities) {
        return buildTools(capabilities, new LinkedHashSet<>());
    }

    /**
     * Translate the enabled capability set into Anthropic server-side tools.
     * Capabilities keyed to another vendor (defensive — the caller already
     * filters by {@code anthropic}) are skipped. Beta-gated tools push their
     * {@code anthropic-beta} token into {@code betas}.
     */
    List<ToolUnion> buildTools(List<EnabledCapability> capabilities, Set<String> betas) {
        List<ToolUnion> tools = new ArrayList<>();
        if (capabilities == null) {
            return tools;
        }
        for (EnabledCapability enabled : capabilities) {
            switch (enabled.capability()) {
                case ANTHROPIC_WEB_SEARCH -> tools.add(ToolUnion.ofWebSearchTool20250305(
                        WebSearchTool20250305.builder().build()));
                case ANTHROPIC_WEB_FETCH -> {
                    tools.add(ToolUnion.ofWebFetchTool20250910(WebFetchTool20250910.builder().build()));
                    betas.add(BETA_WEB_FETCH);
                }
                default -> log.debug("[Native][Anthropic-Messages] capability '{}' is not wired on "
                        + "the Anthropic path — skipping", enabled.capability().getKey());
            }
        }
        return tools;
    }

    /** Concatenate the text of every text content block in the response. */
    String extractText(Message response) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                text.append(block.asText().text());
            }
        }
        return text.toString();
    }

    private long resolveMaxTokens(TurLLMInstance instance) {
        Integer configured = optionsParser.intValue(
                optionsParser.parse(instance.getProviderOptionsJson()), "maxTokens");
        return configured != null && configured > 0 ? configured.longValue() : DEFAULT_MAX_TOKENS;
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }

    private boolean isAssistant(String role) {
        return role != null && "assistant".equals(role.toLowerCase(Locale.ROOT));
    }
}
