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
package com.viglet.turing.genai.nativeapi.anthropic.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolBash20250124;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolTextEditor20250728;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
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
 * T142 / §X.4.d — runs an agentic <b>content-authoring</b> turn through the
 * Anthropic Messages API with the {@code text_editor_20250728} and
 * {@code bash_20250124} client tools.
 *
 * <p>Unlike the one-shot server tools in {@code TurAnthropicMessagesService},
 * these are client-executed and multi-turn: Claude emits {@code tool_use}
 * blocks, Turing executes them and feeds back {@code tool_result} blocks, looping
 * until Claude produces a final answer. Anthropic is stateless, so the full
 * {@link MessageParam} conversation is resent each step (the assistant turn is
 * appended via {@link Message#toParam()}).
 *
 * <p>The {@code text_editor} commands ({@code view}/{@code create}/
 * {@code str_replace}/{@code insert}/{@code undo_edit}) are executed against the
 * per-conversation {@link com.viglet.turing.genai.workspace.TurAgentWorkspace}
 * by {@link TurWorkspaceTextEditor}, so Claude iteratively edits a markdown
 * playbook that persists in (and is served from) the workspace — pair with T120
 * spectator mode to watch the live draft. {@code bash} is delegated to the
 * pluggable {@link TurAgentBashExecutor} seam (no-op default → errors-as-text),
 * since running real shell needs a sandbox that isn't always present.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnthropicEditorService {

    static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    static final long DEFAULT_MAX_TOKENS = 4096L;
    static final int DEFAULT_MAX_ITERATIONS = 16;

    private final TurProviderOptionsParser optionsParser;
    private final TurWorkspaceTextEditor textEditor;
    private final TurAgentBashExecutor bashExecutor;

    public TurAnthropicEditorService(TurProviderOptionsParser optionsParser,
            TurWorkspaceTextEditor textEditor, TurAgentBashExecutor bashExecutor) {
        this.optionsParser = optionsParser;
        this.textEditor = textEditor;
        this.bashExecutor = bashExecutor;
    }

    /**
     * Drive the editor loop and emit Claude's final text as a single SSE token
     * event (CALL semantics — the loop must finish before the answer is known).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance, String agentId,
            String conversationId, List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        Config config = parseConfig(instance, capabilities);
        return Mono.fromCallable(() -> runLoop(client, instance, agentId, conversationId, history,
                        systemPrompt, config))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][Anthropic-Editor] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    String runLoop(AnthropicClient client, TurLLMInstance instance, String agentId,
            String conversationId, List<ChatMessageItem> history, String systemPrompt, Config config) {
        long t0 = System.currentTimeMillis();
        List<ToolUnion> tools = buildTools(config);
        List<MessageParam> conversation = seedConversation(history);
        Map<String, String> undo = new HashMap<>();
        StringBuilder finalText = new StringBuilder();
        int steps = 0;

        for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
            MessageCreateParams params = buildParams(instance, config, tools, systemPrompt, conversation);
            Message response = client.messages().create(params);
            appendMessageText(response, finalText);

            List<ToolUseBlock> calls = toolUseBlocks(response);
            if (calls.isEmpty()) {
                log.info("[Native][Anthropic-Editor] instance '{}' finished after {} tool call(s) in {} ms",
                        instance.getId(), steps, System.currentTimeMillis() - t0);
                return finalText.toString();
            }

            conversation.add(response.toParam());
            List<ContentBlockParam> results = new ArrayList<>(calls.size());
            for (ToolUseBlock call : calls) {
                results.add(executeCall(agentId, conversationId, call, undo));
                steps++;
            }
            conversation.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(results)
                    .build());
        }

        log.info("[Native][Anthropic-Editor] instance '{}' hit the {}-step cap",
                instance.getId(), config.maxIterations());
        return appendCapNote(finalText, config.maxIterations());
    }

    /** Run one tool_use block and wrap its outcome as a tool_result content block. */
    private ContentBlockParam executeCall(String agentId, String conversationId, ToolUseBlock call,
            Map<String, String> undo) {
        Map<?, ?> input = decodeInput(call);
        String output;
        boolean error;
        if ("bash".equals(call.name())) {
            String command = stringValue(input.get("command"));
            TurAgentBashExecutor.BashResult result = bashExecutor.run(agentId, conversationId,
                    command == null ? "" : command);
            output = result.output();
            error = result.error();
        } else {
            TurWorkspaceTextEditor.EditResult result =
                    textEditor.execute(agentId, conversationId, input, undo);
            output = result.output();
            error = result.error();
        }
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(call.id())
                .content(StringUtils.hasText(output) ? output : "(no output)")
                .isError(error)
                .build());
    }

    List<ToolUnion> buildTools(Config config) {
        List<ToolUnion> tools = new ArrayList<>();
        if (config.textEditorEnabled()) {
            tools.add(ToolUnion.ofTextEditor20250728(ToolTextEditor20250728.builder().build()));
        }
        if (config.bashEnabled()) {
            tools.add(ToolUnion.ofBash20250124(ToolBash20250124.builder().build()));
        }
        return tools;
    }

    MessageCreateParams buildParams(TurLLMInstance instance, Config config, List<ToolUnion> tools,
            String systemPrompt, List<MessageParam> conversation) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .messages(conversation);
        for (ToolUnion tool : tools) {
            builder.addTool(tool);
        }
        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            applyTemperature(builder, instance.getTemperature());
        }
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

    private static List<MessageParam> seedConversation(List<ChatMessageItem> history) {
        List<MessageParam> conversation = new ArrayList<>();
        if (history == null) {
            return conversation;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            conversation.add(MessageParam.builder()
                    .role(isAssistant(message.role()) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
                    .content(message.content())
                    .build());
        }
        return conversation;
    }

    private Map<?, ?> decodeInput(ToolUseBlock call) {
        try {
            Map<?, ?> input = call._input().convert(Map.class);
            return input == null ? Map.of() : input;
        } catch (RuntimeException e) {
            log.debug("[Native][Anthropic-Editor] could not decode tool_use input: {}", e.getMessage());
            return Map.of();
        }
    }

    void appendMessageText(Message response, StringBuilder sink) {
        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                sink.append(block.asText().text());
            }
        }
    }

    private static List<ToolUseBlock> toolUseBlocks(Message response) {
        List<ToolUseBlock> calls = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            if (block.isToolUse()) {
                calls.add(block.asToolUse());
            }
        }
        return calls;
    }

    Config parseConfig(TurLLMInstance instance, List<EnabledCapability> capabilities) {
        boolean textEditor = hasCapability(capabilities, TurNativeCapability.ANTHROPIC_TEXT_EDITOR);
        boolean bash = hasCapability(capabilities, TurNativeCapability.ANTHROPIC_BASH);
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        return new Config(
                StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL,
                maxTokens != null && maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS,
                DEFAULT_MAX_ITERATIONS,
                textEditor,
                bash);
    }

    private static boolean hasCapability(List<EnabledCapability> capabilities,
            TurNativeCapability capability) {
        return capabilities != null && capabilities.stream()
                .anyMatch(c -> c.capability() == capability);
    }

    private static boolean isAssistant(String role) {
        return role != null && "assistant".equals(role.toLowerCase(Locale.ROOT));
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String appendCapNote(StringBuilder text, int cap) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append("(Authoring stopped after the ").append(cap)
                .append("-step limit without reaching a final answer.)");
        return text.toString();
    }

    /** Resolved per-turn editor configuration. */
    record Config(String model, long maxTokens, int maxIterations, boolean textEditorEnabled,
            boolean bashEnabled) {
    }
}
