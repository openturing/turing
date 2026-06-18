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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurChatPromptAssembler;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
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
    private final TurChatPromptAssembler promptAssembler;
    private final TurAgentPersonaResolver personaResolver;

    public TurNativeChatExecutor(TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            TurNativeCapabilityService capabilityService,
            TurOpenAiResponsesService openAiResponsesService,
            TurChatPromptAssembler promptAssembler,
            TurAgentPersonaResolver personaResolver) {
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.capabilityService = capabilityService;
        this.openAiResponsesService = openAiResponsesService;
        this.promptAssembler = promptAssembler;
        this.personaResolver = personaResolver;
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
        List<Message> assembled = promptAssembler.assemble(agent, history, conversationId,
                /* flowContext = */ null, activePersona, lastUserMessage, baseSystemPrompt);
        AssembledTurn turn = splitAssembled(assembled);

        log.info("[Native] agent '{}' instance '{}' handled by OpenAI Responses path "
                + "({} capability/ies, persona '{}')",
                agent.getTitle(), instance.getId(), capabilities.size(),
                activePersona == null ? "none" : activePersona.getName());
        return Optional.of(openAiResponsesService.chat(client.get(), instance, turn.history(),
                turn.systemPrompt(), capabilities));
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
