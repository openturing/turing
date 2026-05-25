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
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.service.chatmemory.TurChatMemoryRelevanceRetriever;

/**
 * Composes the {@code List<Message>} that ships to the LLM:
 * <ol>
 *   <li>Fuses the agent / RAG-override base prompt with the flow-engine's
 *       per-node addendum (T31 cached) and the active persona's voice +
 *       constraints + brand-context block (T31 cached).</li>
 *   <li>Enriches the history with the T30 conversation-relevance retriever
 *       so older turns relevant to the latest user message get pinned into
 *       the prompt.</li>
 *   <li>Builds the ordered {@code List<Message>} (system + history fan-out
 *       into {@code UserMessage}/{@code AssistantMessage}).</li>
 * </ol>
 *
 * <p>Extracted from {@link TurAgentChatExecutor} in T33. Owns the
 * {@link TurMeterNames#STAGE_CHAT_SETUP_COMPOSE} substage timing — measured
 * around the {@link TurPersonaPromptComposer#compose} call (matches the
 * pre-T33 boundary so dashboards keep reading the same number).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurChatPromptAssembler {

    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurPersonaPromptComposer personaPromptComposer;
    private final TurChatMemoryRelevanceRetriever chatMemoryRelevanceRetriever;
    private final TurChatPipelineObservation chatPipelineObservation;

    public TurChatPromptAssembler(TurChatFlowEngineService chatFlowEngineService,
            TurPersonaPromptComposer personaPromptComposer,
            TurChatMemoryRelevanceRetriever chatMemoryRelevanceRetriever,
            TurChatPipelineObservation chatPipelineObservation) {
        this.chatFlowEngineService = chatFlowEngineService;
        this.personaPromptComposer = personaPromptComposer;
        this.chatMemoryRelevanceRetriever = chatMemoryRelevanceRetriever;
        this.chatPipelineObservation = chatPipelineObservation;
    }

    /**
     * Produce the final {@code List<Message>} for the LLM call.
     *
     * @param agent the chatting agent (used by T30 for relevance opt-in)
     * @param history the persisted conversation history (untouched by the assembler)
     * @param conversationId nullable — null means stateless turn / no T30 enrichment
     * @param flowContext nullable — null means no chat flow governs this turn
     * @param effectivePersona the post-walk persona (single resolution per §I.5 step 2)
     * @param lastUserMessage the latest user message (drives T30 relevance + few-shot examples)
     * @param baseSystemPrompt either the agent's own system prompt or a RAG override
     */
    public List<Message> assemble(TurAIAgent agent,
            List<ChatMessageItem> history,
            String conversationId,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona,
            String lastUserMessage,
            String baseSystemPrompt) {
        long t0 = System.currentTimeMillis();
        String flowAugmented = flowContext == null
                ? baseSystemPrompt
                : baseSystemPrompt + chatFlowEngineService.buildSystemPromptAddendum(
                        flowContext.flow(), flowContext.state(), flowContext.graph());
        // Fuse the persona on top of everything: its instruction + style
        // guidelines + vocabulary constraints + brand-context hint +
        // few-shot examples wrap the (already RAG-augmented) base prompt.
        // No-op when effectivePersona is null.
        String resolvedSystemPrompt = personaPromptComposer.compose(
                effectivePersona,
                agent.getTurEmbeddingModelInstance(),
                lastUserMessage,
                flowAugmented);
        long elapsed = System.currentTimeMillis() - t0;
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_COMPOSE, elapsed);

        // T30 / §IV.4 — conversation-relevance retrieval. When the agent has
        // opted in AND the persisted store has more turns than the recent-N
        // FIFO window covers, prepend the BM25-top-K older turns (each
        // tagged with a position/timestamp marker) so the LLM keeps context
        // from earlier in a long conversation. No-op when disabled or when
        // the store has nothing older than the recent window.
        List<ChatMessageItem> effectiveHistory = chatMemoryRelevanceRetriever.enrich(
                agent, conversationId, lastUserMessage, history);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resolvedSystemPrompt));
        for (ChatMessageItem item : effectiveHistory) {
            String content = item.content() == null ? "" : item.content();
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }
}
