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

package com.viglet.turing.genai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurLoggingToolCallback;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Shared executor for agent-driven chats. Centralizes:
 * <ul>
 *   <li>Resolving the agent's tool callbacks (native + MCP).</li>
 *   <li>Building the chat model from an LLM instance + decrypted API key.</li>
 *   <li>Assembling the {@link Prompt} with system/user/assistant messages.</li>
 *   <li>Streaming the response as {@code Flux<ChatResponse>} and recording
 *       token usage.</li>
 * </ul>
 *
 * Both the standalone agent chat ({@code /api/v2/ai-agent/{id}/chat}) and the
 * SN site AI Mode chat ({@code /api/sn/{site}/chat/conversation}) call into
 * this — the SN flow simply augments the system prompt with retrieved RAG
 * context before delegating.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurAgentChatExecutor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an AI assistant. Answer the user's questions using the tools available to you.
            If you have access to MCP server tools, use them when relevant to fulfill the user's request.
            If the user asks in a specific language, respond in that same language.
            """;

    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurNativeToolService nativeToolService;
    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurLLMTokenUsageService tokenUsageService;

    public TurAgentChatExecutor(TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurLLMTokenUsageService tokenUsageService) {
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.tokenUsageService = tokenUsageService;
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content) {
    }

    /** Resolve the union of native + MCP tool callbacks attached to the agent. */
    public ToolCallback[] resolveToolCallbacks(TurAIAgent agent) {
        Set<String> selectedNativeTools = parseNativeTools(agent.getNativeTools());
        ToolCallback[] nativeCallbacks = selectedNativeTools.isEmpty()
                ? new ToolCallback[0]
                : nativeToolService.getToolCallbacks(selectedNativeTools);
        ToolCallback[] mcpCallbacks = agent.getMcpServers() == null || agent.getMcpServers().isEmpty()
                ? new ToolCallback[0]
                : mcpToolCallbackService.getToolCallbacks(agent.getMcpServers());
        return TurLoggingToolCallback.wrap(
                Stream.concat(Arrays.stream(nativeCallbacks), Arrays.stream(mcpCallbacks))
                        .toArray(ToolCallback[]::new));
    }

    /**
     * Run an agent-driven chat. The {@code systemPromptOverride}, when
     * provided, replaces the agent's own {@code TurAIAgent.getSystemPrompt()}.
     * Use this from RAG flows that need to embed retrieved chunks in the
     * system message.
     *
     * @return a single-emission {@link Flux} carrying the assistant response
     *         (matches Spring AI's call-not-stream model so tool calling is
     *         resolved end-to-end before the response is emitted).
     */
    public Flux<ChatResponse> execute(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride) {

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(llmInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        ChatModel chatModel = provider.createChatModel(llmInstance, decryptedApiKey);

        ToolCallback[] callbacks = resolveToolCallbacks(agent);
        log.info("[AgentExec] Agent '{}': {} tool callbacks", agent.getTitle(), callbacks.length);
        for (ToolCallback cb : callbacks) {
            log.info("[AgentExec] Available tool: '{}'", cb.getToolDefinition().name());
        }

        var chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(true)
                .build();

        String resolvedSystemPrompt = systemPromptOverride != null && !systemPromptOverride.isBlank()
                ? systemPromptOverride
                : (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()
                        ? agent.getSystemPrompt()
                        : DEFAULT_SYSTEM_PROMPT);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resolvedSystemPrompt));
        for (ChatMessageItem item : history) {
            String content = item.content() == null ? "" : item.content();
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }

        Prompt prompt = new Prompt(messages, chatOptions);
        String username = resolveUsername();

        return Mono.fromCallable(() -> {
            log.info("[AgentExec] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), llmInstance.getTitle());
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(llmInstance, response, username);

            String text = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText()
                            : "";
            log.info("[AgentExec] Agent '{}' response: {} chars",
                    agent.getTitle(), text.length());
            return new ChatResponse("assistant", text);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentExec] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .filter(response -> !response.content().isEmpty())
                .flux();
    }

    private static Set<String> parseNativeTools(String nativeTools) {
        if (nativeTools == null || nativeTools.isBlank()) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        for (String tool : nativeTools.split(",")) {
            String trimmed = tool.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}
