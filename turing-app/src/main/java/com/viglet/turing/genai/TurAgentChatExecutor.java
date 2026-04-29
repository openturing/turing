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
import java.util.Optional;
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

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurLoggingToolCallback;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
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

    /**
     * Sentinel value the front-end sends as {@code flowId} when the user
     * picks "Sem fluxo" in the chat dropdown — the auto-trigger router must
     * be skipped entirely.
     */
    public static final String FLOW_ID_NONE = "__none__";

    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurNativeToolService nativeToolService;
    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurCustomToolCallbackService customToolCallbackService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;

    public TurAgentChatExecutor(TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurCustomToolCallbackService customToolCallbackService,
            TurLLMTokenUsageService tokenUsageService,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService) {
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.customToolCallbackService = customToolCallbackService;
        this.tokenUsageService = tokenUsageService;
        this.chatFlowRepository = chatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content) {
    }

    /** Resolve the union of native + MCP + custom tool callbacks attached to the agent. */
    public ToolCallback[] resolveToolCallbacks(TurAIAgent agent) {
        Set<String> selectedNativeTools = parseNativeTools(agent.getNativeTools());
        ToolCallback[] nativeCallbacks = selectedNativeTools.isEmpty()
                ? new ToolCallback[0]
                : nativeToolService.getToolCallbacks(selectedNativeTools);
        ToolCallback[] mcpCallbacks = agent.getMcpServers() == null || agent.getMcpServers().isEmpty()
                ? new ToolCallback[0]
                : mcpToolCallbackService.getToolCallbacks(agent.getMcpServers());
        ToolCallback[] customCallbacks = agent.getCustomTools() == null || agent.getCustomTools().isEmpty()
                ? new ToolCallback[0]
                : customToolCallbackService.getToolCallbacks(agent.getCustomTools());
        return TurLoggingToolCallback.wrap(
                Stream.of(nativeCallbacks, mcpCallbacks, customCallbacks)
                        .flatMap(Arrays::stream)
                        .toArray(ToolCallback[]::new));
    }

    /**
     * Run an agent-driven chat without a chat flow attached. Convenience
     * wrapper kept for the SN site AI Mode (RAG) flow that doesn't use the
     * Phase B engine.
     */
    public Flux<ChatResponse> execute(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride) {
        return execute(agent, llmInstance, history, systemPromptOverride, null, null);
    }

    /**
     * Run an agent-driven chat. The {@code systemPromptOverride}, when
     * provided, replaces the agent's own {@code TurAIAgent.getSystemPrompt()}.
     * Use this from RAG flows that need to embed retrieved chunks in the
     * system message.
     *
     * <p>When both {@code conversationId} and {@code flowId} are non-blank
     * and the flow is reachable, the runtime engine augments the system
     * prompt with the current node's contract and advances the persisted
     * state after the LLM responds.
     *
     * @return a single-emission {@link Flux} carrying the assistant response
     *         (matches Spring AI's call-not-stream model so tool calling is
     *         resolved end-to-end before the response is emitted).
     */
    public Flux<ChatResponse> execute(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride,
            String conversationId,
            String flowId) {

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

        String baseSystemPrompt = resolveSystemPrompt(agent, systemPromptOverride);
        String lastUserMessageEarly = lastMessageOfRole(history, "user");
        FlowContext flowContext = resolveFlowContext(agent, conversationId, flowId,
                lastUserMessageEarly, chatModel);
        String resolvedSystemPrompt = flowContext == null
                ? baseSystemPrompt
                : baseSystemPrompt + chatFlowEngineService.buildSystemPromptAddendum(
                        flowContext.flow(), flowContext.state(), flowContext.graph());

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
        // Reuse the value already computed for the router; both paths need it.
        final String lastUserMessage = lastUserMessageEarly;

        return Mono.fromCallable(() -> {
            log.info("[AgentExec] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), llmInstance.getTitle());
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(llmInstance, response, username);

            String text = "";
            if (response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null) {
                text = response.getResult().getOutput().getText();
            }
            log.info("[AgentExec] Agent '{}' response: {} chars",
                    agent.getTitle(), text.length());

            if (flowContext != null) {
                var advance = chatFlowEngineService.advance(flowContext.flow(),
                        flowContext.state(), flowContext.graph(),
                        lastUserMessage, text, chatModel);
                // LLM_JUDGE may rewrite the assistant text when it judges the
                // reply off-topic; HEURISTIC always leaves the override null.
                if (advance.assistantOverride() != null) {
                    text = advance.assistantOverride();
                }
            }

            return new ChatResponse("assistant", text);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentExec] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .filter(response -> !response.content().isEmpty())
                .flux();
    }

    private static String resolveSystemPrompt(TurAIAgent agent, String systemPromptOverride) {
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            return systemPromptOverride;
        }
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            return agent.getSystemPrompt();
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * Resolves which flow (if any) should govern this chat turn, with three
     * paths:
     *
     * <ul>
     *   <li>{@code flowId == FLOW_ID_NONE}: user explicitly opted out in the
     *       dropdown → no flow runs and the router is skipped.</li>
     *   <li>{@code flowId != null} and not blank: forced selection — used to
     *       simulate a specific path. Loads the flow and inits state.</li>
     *   <li>{@code flowId == null/blank}: auto mode — the engine's router
     *       picks a flow based on each flow's {@code triggerDescription},
     *       continues an in-progress flow if any, or returns no selection.</li>
     * </ul>
     *
     * Returns null in every case where no flow should be applied.
     */
    private FlowContext resolveFlowContext(TurAIAgent agent,
            String conversationId,
            String flowId,
            String lastUserMessage,
            ChatModel routerModel) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        if (FLOW_ID_NONE.equals(flowId)) {
            return null;
        }
        if (flowId != null && !flowId.isBlank()) {
            return resolveForcedFlow(agent, conversationId, flowId);
        }
        // Auto mode: ask the engine's router which flow (if any) fits.
        return chatFlowEngineService
                .selectActiveFlow(agent, conversationId, lastUserMessage, routerModel)
                .map(s -> new FlowContext(s.flow(), s.graph(), s.state()))
                .orElse(null);
    }

    private FlowContext resolveForcedFlow(TurAIAgent agent, String conversationId, String flowId) {
        Optional<TurChatFlow> flowOpt = chatFlowRepository.findById(flowId);
        if (flowOpt.isEmpty()) {
            log.warn("[AgentExec] Chat flow '{}' not found", flowId);
            return null;
        }
        TurChatFlow flow = flowOpt.get();
        // Defense in depth: a flow only governs chats for its owning agent.
        if (flow.getTurAIAgent() == null || !agent.getId().equals(flow.getTurAIAgent().getId())) {
            log.warn("[AgentExec] Flow '{}' does not belong to agent '{}'", flowId, agent.getId());
            return null;
        }
        if (flow.getEnabled() != 1) {
            log.info("[AgentExec] Flow '{}' is disabled — ignoring", flowId);
            return null;
        }
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            log.warn("[AgentExec] Flow '{}' has no parseable graph — ignoring", flowId);
            return null;
        }
        Optional<TurChatFlowState> stateOpt = chatFlowEngineService.loadOrInitState(
                conversationId, flow, graphOpt.get());
        if (stateOpt.isEmpty()) {
            log.warn("[AgentExec] Could not initialize flow state for '{}'/'{}'",
                    conversationId, flowId);
            return null;
        }
        return new FlowContext(flow, graphOpt.get(), stateOpt.get());
    }

    private static String lastMessageOfRole(List<ChatMessageItem> history, String role) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem item = history.get(i);
            if (role.equals(item.role())) {
                return item.content() == null ? "" : item.content();
            }
        }
        return "";
    }

    /** Resolved per-turn flow runtime — group of values that always travel together. */
    private record FlowContext(TurChatFlow flow, ChatFlowGraph graph, TurChatFlowState state) {
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
