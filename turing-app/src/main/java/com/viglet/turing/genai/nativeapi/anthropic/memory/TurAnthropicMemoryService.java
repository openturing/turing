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
package com.viglet.turing.genai.nativeapi.anthropic.memory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMemoryTool20250818;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement.Options;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * T163 / §X.9.a — runs a chat turn through the Anthropic <b>beta Messages API</b>
 * with the {@code memory_20250818} client tool, so Claude keeps a durable file
 * scratchpad ("what this agent remembers about you") that survives across turns.
 *
 * <p>Like {@link com.viglet.turing.genai.nativeapi.anthropic.editor.TurAnthropicEditorService
 * the editor loop} this is a client-executed multi-turn tool: Claude emits a
 * {@code memory} {@code tool_use} block (one of {@code view}/{@code create}/
 * {@code str_replace}/{@code insert}/{@code delete}/{@code rename}), Turing runs
 * it against the per-conversation
 * {@link com.viglet.turing.genai.workspace.TurAgentWorkspace} via
 * {@link TurWorkspaceMemoryHandler}, and feeds back a {@code tool_result} — looping
 * until Claude produces a final answer. The memory tool needs the beta Messages
 * API and the {@code context-management-2025-06-27} beta flag, so this mirrors
 * {@code TurAnthropicComputerUseService} (also beta): the full
 * {@link BetaMessageParam} conversation is resent every step (Anthropic is
 * stateless), each response appended via {@link BetaMessage#toParam()}.
 *
 * <p>The conversation id the handler is scoped to is supplied by the caller; the
 * default is the live conversation (per-conversation memory). T166 reuses this
 * service with a per-user scope by passing a user-derived id instead.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnthropicMemoryService {

    /** A Claude model that supports the memory tool; overridable via the instance model. */
    static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    static final long DEFAULT_MAX_TOKENS = 4096L;
    static final int DEFAULT_MAX_ITERATIONS = 16;

    private final TurProviderOptionsParser optionsParser;
    private final TurWorkspaceMemoryHandler memoryHandler;
    private final TurAnthropicContextManagement contextManagement;

    public TurAnthropicMemoryService(TurProviderOptionsParser optionsParser,
            TurWorkspaceMemoryHandler memoryHandler,
            TurAnthropicContextManagement contextManagement) {
        this.optionsParser = optionsParser;
        this.memoryHandler = memoryHandler;
        this.contextManagement = contextManagement;
    }

    /**
     * Drive the memory loop and emit Claude's final text as a single SSE token
     * event (CALL semantics — the loop must finish before the answer is known).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance, String agentId,
            String conversationId, List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, Options contextOptions) {
        Config config = parseConfig(instance);
        Options options = contextOptions == null ? Options.NONE : contextOptions;
        return Mono.fromCallable(() -> runLoop(client, instance, agentId, conversationId, history,
                        systemPrompt, config, options))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][Anthropic-Memory] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    String runLoop(AnthropicClient client, TurLLMInstance instance, String agentId,
            String conversationId, List<ChatMessageItem> history, String systemPrompt, Config config,
            Options contextOptions) {
        long t0 = System.currentTimeMillis();
        BetaMemoryTool20250818 memoryTool = BetaMemoryTool20250818.builder().build();
        List<BetaMessageParam> conversation = seedConversation(history);
        StringBuilder finalText = new StringBuilder();
        int steps = 0;

        for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
            MessageCreateParams params = buildParams(instance, config, memoryTool, systemPrompt,
                    conversation, contextOptions);
            BetaMessage response = client.beta().messages().create(params);
            appendMessageText(response, finalText);

            List<BetaToolUseBlock> calls = toolUseBlocks(response);
            if (calls.isEmpty()) {
                log.info("[Native][Anthropic-Memory] instance '{}' finished after {} memory op(s) in {} ms",
                        instance.getId(), steps, System.currentTimeMillis() - t0);
                return finalText.toString();
            }

            conversation.add(response.toParam());
            List<BetaContentBlockParam> results = new ArrayList<>(calls.size());
            for (BetaToolUseBlock call : calls) {
                results.add(executeCall(agentId, conversationId, call));
                steps++;
            }
            conversation.add(BetaMessageParam.builder()
                    .role(BetaMessageParam.Role.USER)
                    .contentOfBetaContentBlockParams(results)
                    .build());
        }

        log.info("[Native][Anthropic-Memory] instance '{}' hit the {}-step cap",
                instance.getId(), config.maxIterations());
        return appendCapNote(finalText, config.maxIterations());
    }

    /** Run one memory tool_use block and wrap its outcome as a tool_result content block. */
    private BetaContentBlockParam executeCall(String agentId, String conversationId,
            BetaToolUseBlock call) {
        Map<?, ?> input = decodeInput(call);
        TurWorkspaceMemoryHandler.MemoryResult result =
                memoryHandler.execute(agentId, conversationId, input);
        return BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder()
                .toolUseId(call.id())
                .content(StringUtils.hasText(result.output()) ? result.output() : "(no output)")
                .isError(result.error())
                .build());
    }

    MessageCreateParams buildParams(TurLLMInstance instance, Config config,
            BetaMemoryTool20250818 memoryTool, String systemPrompt, List<BetaMessageParam> conversation,
            Options contextOptions) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .addTool(memoryTool)
                .messages(conversation);
        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            applyTemperature(builder, instance.getTemperature());
        }
        // The memory tool always needs the context-management beta; T164/T165 add
        // the opt-in context-editing / compaction edits (and the compaction beta)
        // on top. A LinkedHashSet dedups the shared context-management flag.
        Set<AnthropicBeta> betas = new LinkedHashSet<>();
        betas.add(AnthropicBeta.CONTEXT_MANAGEMENT_2025_06_27);
        betas.addAll(contextManagement.betas(contextOptions));
        betas.forEach(builder::addBeta);
        contextManagement.betaConfig(contextOptions).ifPresent(builder::contextManagement);
        return builder.build();
    }

    /**
     * Apply the per-instance temperature. The Anthropic SDK deprecated
     * {@code temperature(double)} because models after Claude Opus 4.6 reject any
     * non-1.0 temperature (HTTP 400). Turing still honours the configured
     * temperature deliberately — for the many older Claude models, and
     * OpenAI-compatible Anthropic proxies, that accept it — so the deprecation is
     * suppressed here, scoped to this one call to keep the signal for any other
     * deprecated API elsewhere.
     */
    @SuppressWarnings("deprecation")
    private static void applyTemperature(MessageCreateParams.Builder builder, double temperature) {
        builder.temperature(temperature);
    }

    private static List<BetaMessageParam> seedConversation(List<ChatMessageItem> history) {
        List<BetaMessageParam> conversation = new ArrayList<>();
        if (history == null) {
            return conversation;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            conversation.add(BetaMessageParam.builder()
                    .role(isAssistant(message.role())
                            ? BetaMessageParam.Role.ASSISTANT
                            : BetaMessageParam.Role.USER)
                    .content(message.content())
                    .build());
        }
        return conversation;
    }

    private Map<?, ?> decodeInput(BetaToolUseBlock call) {
        try {
            Map<?, ?> input = call.input(Map.class);
            return input == null ? Map.of() : input;
        } catch (RuntimeException e) {
            log.debug("[Native][Anthropic-Memory] could not decode tool_use input: {}", e.getMessage());
            return Map.of();
        }
    }

    void appendMessageText(BetaMessage response, StringBuilder sink) {
        for (BetaContentBlock block : response.content()) {
            if (block.isText()) {
                sink.append(block.asText().text());
            }
        }
    }

    private static List<BetaToolUseBlock> toolUseBlocks(BetaMessage response) {
        List<BetaToolUseBlock> calls = new ArrayList<>();
        for (BetaContentBlock block : response.content()) {
            if (block.isToolUse()) {
                calls.add(block.asToolUse());
            }
        }
        return calls;
    }

    Config parseConfig(TurLLMInstance instance) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        return new Config(
                StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL,
                maxTokens != null && maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS,
                DEFAULT_MAX_ITERATIONS);
    }

    private static boolean isAssistant(String role) {
        return role != null && "assistant".equals(role.toLowerCase(Locale.ROOT));
    }

    private static String appendCapNote(StringBuilder text, int cap) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append("(Memory loop stopped after the ").append(cap)
                .append("-step limit without reaching a final answer.)");
        return text.toString();
    }

    /** Resolved per-turn memory configuration. */
    record Config(String model, long maxTokens, int maxIterations) {
    }
}
