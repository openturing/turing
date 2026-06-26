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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurChatPromptAssembler;
import com.viglet.turing.genai.TurChatPromptRequest;
import com.viglet.turing.genai.TurChatToolResolver;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver;
import com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver.NativeSelection;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicMessagesService;
import com.viglet.turing.genai.nativeapi.anthropic.computeruse.TurAnthropicComputerUseService;
import com.viglet.turing.genai.nativeapi.anthropic.editor.TurAnthropicEditorService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;

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
 * unchanged until an admin flips a capability row on.
 *
 * <p>T131 / §X.2 — when it <em>does</em> handle the turn it now composes the
 * system prompt + history through the same post-T33 collaborator the Spring AI
 * path uses ({@link TurChatPromptAssembler}, fed by the single per-turn
 * {@link TurAgentPersonaResolver} resolution). That fuses the RAG override
 * base, persona voice + brand-context, MCP server instructions, rich-content
 * rendering guidance and the chat-memory layers (T30 relevance retrieval +
 * T115 compression) onto the native input — so a turn reads identically
 * whether it dispatches through Spring AI or the native OpenAI Responses SDK.
 * The flow harness ({@code flowContext}) is intentionally left {@code null}:
 * the chat REST controller only routes <b>plain</b> turns (no attachments, no
 * chat flow) into the native path, and the Responses built-in tools have no
 * equivalent of the Spring AI tool-execution loop the flow engine drives.
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
    private final TurOpenAiComputerUseService openAiComputerUseService;
    private final TurAnthropicMessagesService anthropicMessagesService;
    private final TurAnthropicComputerUseService anthropicComputerUseService;
    private final TurAnthropicEditorService anthropicEditorService;
    private final TurChatPromptAssembler promptAssembler;
    private final TurAgentPersonaResolver personaResolver;
    private final TurNativeCapabilityResolver capabilityResolver;
    private final TurChatToolResolver toolResolver;

    public TurNativeChatExecutor(TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            TurNativeCapabilityService capabilityService,
            TurOpenAiResponsesService openAiResponsesService,
            TurOpenAiComputerUseService openAiComputerUseService,
            TurAnthropicMessagesService anthropicMessagesService,
            TurAnthropicComputerUseService anthropicComputerUseService,
            TurAnthropicEditorService anthropicEditorService,
            TurChatPromptAssembler promptAssembler,
            TurAgentPersonaResolver personaResolver,
            TurNativeCapabilityResolver capabilityResolver,
            TurChatToolResolver toolResolver) {
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.capabilityService = capabilityService;
        this.openAiResponsesService = openAiResponsesService;
        this.openAiComputerUseService = openAiComputerUseService;
        this.anthropicMessagesService = anthropicMessagesService;
        this.anthropicComputerUseService = anthropicComputerUseService;
        this.anthropicEditorService = anthropicEditorService;
        this.promptAssembler = promptAssembler;
        this.personaResolver = personaResolver;
        this.capabilityResolver = capabilityResolver;
        this.toolResolver = toolResolver;
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
        boolean isOpenAi = "openai".equals(pluginType);
        boolean isAnthropic = "anthropic".equals(pluginType);
        if (!isOpenAi && !isAnthropic) {
            // Only OpenAI (F.2) and Anthropic (F.3) have a native path; other
            // vendors always fall through to the Spring AI executor.
            return Optional.empty();
        }

        List<EnabledCapability> instanceCapabilities =
                capabilityService.enabledFor(instance.getId(), pluginType);
        if (instanceCapabilities.isEmpty()) {
            return Optional.empty();
        }

        // T433 / §X.18.b — resolve the per-agent selection over what the instance
        // is capable of. Legacy (no explicit selection) keeps the pre-T433
        // winner-takes-all set; an explicit selection narrows to
        // agentSelected ∩ instanceCapable and may attach coexisting Turing tools.
        boolean legacy = capabilityResolver.isLegacy(agent.getNativeCapabilities());
        NativeSelection selection = legacy
                ? null
                : capabilityResolver.resolve(agent.getNativeCapabilities(), instanceCapabilities);
        if (selection != null && selection.isEmpty()) {
            // The agent opted in but none of its picks are enabled on this
            // instance — defer to the Spring AI path (which runs the agent's
            // normal tool set) rather than a native turn with no capabilities.
            return Optional.empty();
        }
        List<EnabledCapability> capabilities = selection == null
                ? instanceCapabilities
                : selection.nativeCapabilities();

        // Acquire the vendor's native client up front; a missing/unusable client
        // (e.g. no API key) means we can't handle the turn natively.
        Optional<OpenAIClient> openAiClient = isOpenAi ? nativeClient.openAi(instance) : Optional.empty();
        Optional<AnthropicClient> anthropicClient = isAnthropic
                ? nativeClient.anthropic(instance) : Optional.empty();
        if (isOpenAi && openAiClient.isEmpty()) {
            return Optional.empty();
        }
        if (isAnthropic && anthropicClient.isEmpty()) {
            return Optional.empty();
        }

        // T131 / §X.2 — fuse the post-T33 prompt-assembly collaborator onto the
        // native path. The base prompt is the RAG override (when the SN AI Mode
        // flow passes one) or the agent's own system prompt; the assembler then
        // layers on MCP instructions, rich-content guidance, persona voice and
        // the chat-memory turns. Persona is resolved once per turn, mirroring
        // §I.5 step 2 of the Spring AI executor. The flow context is null on the
        // native path (see class javadoc).
        String baseSystemPrompt = StringUtils.hasText(systemPromptOverride)
                ? systemPromptOverride
                : agent.getSystemPrompt();
        TurPersona activePersona = personaResolver.resolve(agent, conversationId);
        String lastUserMessage = lastMessageOfRole(history, "user");
        List<Message> assembled = promptAssembler.assemble(
                new TurChatPromptRequest(agent, history, conversationId,
                        /* flowContext = */ null, activePersona, lastUserMessage, baseSystemPrompt));
        AssembledTurn turn = splitAssembled(assembled);

        // T433 / §X.18.b — "capacidade é mutex, resto coexiste". For an explicit
        // (non-legacy) selection whose picks don't own the turn, attach the
        // agent's Turing/MCP/custom tools whose function isn't claimed by a
        // selected provider-native capability and run them through the native
        // tool-execution loop. Legacy turns (and owns-turn picks) attach none —
        // identical to the pre-T433 one-shot behaviour.
        ToolCallback[] coexistingTools =
                resolveCoexistingTools(selection, agent, activePersona, lastUserMessage);

        // F.3 / §X.4 — Anthropic path.
        if (isAnthropic) {
            // T143 / §X.4.e — computer_use is a multi-turn screenshot/action loop,
            // not a one-shot server tool (and uses the beta Messages API), so it
            // owns the whole turn when enabled — exactly like the OpenAI variant.
            Optional<EnabledCapability> computerUse = capabilities.stream()
                    .filter(c -> c.capability() == TurNativeCapability.ANTHROPIC_COMPUTER_USE)
                    .findFirst();
            if (computerUse.isPresent()) {
                if (capabilities.size() > 1) {
                    log.info("[Native] agent '{}' instance '{}' has Anthropic computer_use enabled "
                            + "alongside {} other native capability/ies — driving the computer-use "
                            + "loop only this turn", agent.getTitle(), instance.getId(),
                            capabilities.size() - 1);
                }
                log.info("[Native] agent '{}' instance '{}' handled by Anthropic computer-use loop "
                        + "(persona '{}')", agent.getTitle(), instance.getId(),
                        activePersona == null ? "none" : activePersona.getName());
                return Optional.of(anthropicComputerUseService.chat(anthropicClient.get(), instance,
                        turn.history(), turn.systemPrompt(), computerUse.get()));
            }

            // T142 / §X.4.d — text_editor / bash are client-executed multi-turn
            // tools (the editor loop drives them against TurAgentWorkspace + the
            // bash seam), so they own the turn when enabled.
            boolean hasEditorTool = capabilities.stream().anyMatch(c ->
                    c.capability() == TurNativeCapability.ANTHROPIC_TEXT_EDITOR
                            || c.capability() == TurNativeCapability.ANTHROPIC_BASH);
            if (hasEditorTool) {
                log.info("[Native] agent '{}' instance '{}' handled by Anthropic editor loop "
                        + "(persona '{}')", agent.getTitle(), instance.getId(),
                        activePersona == null ? "none" : activePersona.getName());
                return Optional.of(anthropicEditorService.chat(anthropicClient.get(), instance,
                        agent.getId(), conversationId, turn.history(), turn.systemPrompt(), capabilities));
            }

            // The remaining Anthropic server-side tools (web_search, web_fetch,
            // code_execution) execute inside Anthropic's infrastructure and return
            // the answer in a single turn — no per-tool branching.
            log.info("[Native] agent '{}' instance '{}' handled by Anthropic Messages path "
                    + "({} capability/ies, persona '{}')",
                    agent.getTitle(), instance.getId(), capabilities.size(),
                    activePersona == null ? "none" : activePersona.getName());
            return Optional.of(anthropicMessagesService.chat(anthropicClient.get(), instance,
                    turn.history(), turn.systemPrompt(), capabilities, coexistingTools));
        }

        // T136 / §X.3.d — computer_use is not a one-shot built-in: it needs the
        // multi-turn screenshot/action loop. When it is enabled, the whole turn
        // is driven by the dedicated computer-use service (the other native
        // tools can't share its computer-use-preview model / request shape, so
        // they're ignored for this turn — logged below).
        Optional<EnabledCapability> computerUse = capabilities.stream()
                .filter(c -> c.capability() == TurNativeCapability.OPENAI_COMPUTER_USE)
                .findFirst();
        if (computerUse.isPresent()) {
            if (capabilities.size() > 1) {
                log.info("[Native] agent '{}' instance '{}' has computer_use enabled alongside {} "
                        + "other native capability/ies — driving the computer-use loop only this turn",
                        agent.getTitle(), instance.getId(), capabilities.size() - 1);
            }
            log.info("[Native] agent '{}' instance '{}' handled by OpenAI computer-use loop (persona '{}')",
                    agent.getTitle(), instance.getId(),
                    activePersona == null ? "none" : activePersona.getName());
            return Optional.of(openAiComputerUseService.chat(openAiClient.get(), instance, turn.history(),
                    turn.systemPrompt(), computerUse.get()));
        }

        log.info("[Native] agent '{}' instance '{}' handled by OpenAI Responses path "
                + "({} capability/ies, persona '{}')",
                agent.getTitle(), instance.getId(), capabilities.size(),
                activePersona == null ? "none" : activePersona.getName());
        return Optional.of(openAiResponsesService.chat(openAiClient.get(), instance, turn.history(),
                turn.systemPrompt(), capabilities, coexistingTools));
    }

    /**
     * T433 / §X.18.b — the agent's Turing/MCP/custom tools that coexist with the
     * selected provider-native picks this turn. Empty for the legacy path
     * ({@code selection == null}) and for owns-turn picks (computer_use), so the
     * native services run their one-shot server-tool turn unchanged. Otherwise
     * resolves the agent's full tool set (same collaborator the Spring AI path
     * uses, BM25 pre-filter included) and drops every callback whose abstract
     * {@code function} is claimed by a selected provider-native capability.
     */
    private ToolCallback[] resolveCoexistingTools(NativeSelection selection, TurAIAgent agent,
            TurPersona activePersona, String lastUserMessage) {
        if (selection == null || selection.hasOwnsTurn()) {
            return new ToolCallback[0];
        }
        ToolCallback[] agentTools = toolResolver.resolve(agent, activePersona, lastUserMessage, null);
        return capabilityResolver.coexistingTools(agentTools, selection.claimedFunctions());
    }

    /**
     * The native Responses API takes a {@code instructions} string + a flat
     * input-item list, not Spring AI {@code Message} objects. Split the
     * assembler's output back into those two shapes: the leading
     * {@link MessageType#SYSTEM} message becomes the instructions, and every
     * remaining message maps to a {@link ChatMessageItem} (user / assistant
     * role). Media blocks attached to a user message are dropped — the native
     * path only handles plain turns (no attachments), so there are none.
     */
    private static AssembledTurn splitAssembled(List<Message> messages) {
        String systemPrompt = "";
        List<ChatMessageItem> history = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String text = message.getText() == null ? "" : message.getText();
            MessageType type = message.getMessageType();
            switch (type) {
                // Keep the first (and only) system message as the instructions.
                case SYSTEM -> systemPrompt = text;
                case ASSISTANT -> history.add(new ChatMessageItem("assistant", text));
                default -> history.add(new ChatMessageItem("user", text));
            }
        }
        return new AssembledTurn(systemPrompt, history);
    }

    private record AssembledTurn(String systemPrompt, List<ChatMessageItem> history) {
    }

    private static String lastMessageOfRole(List<ChatMessageItem> history, String role) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem item = history.get(i);
            if (item != null && role.equals(item.role())) {
                return item.content() == null ? "" : item.content();
            }
        }
        return "";
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
