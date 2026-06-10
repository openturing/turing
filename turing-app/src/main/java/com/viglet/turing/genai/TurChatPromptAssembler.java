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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.genai.tool.TurMcpInstructionsProvider;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;
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
@Slf4j
@Service
public class TurChatPromptAssembler {

    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurPersonaPromptComposer personaPromptComposer;
    private final TurChatMemoryRelevanceRetriever chatMemoryRelevanceRetriever;
    private final TurChatMemoryCompressionService chatMemoryCompressionService;
    private final TurChatPipelineObservation chatPipelineObservation;
    private final TurChatAttachmentService chatAttachmentService;
    private final TurMcpInstructionsProvider mcpInstructionsProvider;
    private final TurSkillRunnerService skillRunnerService;

    /**
     * The rich-content rendering guidance ({@code ```html} live preview /
     * {@code ```d2} diagrams / Chart.js). Appended to an agent's system prompt
     * only when {@link TurAIAgent#isRichContentEnabled()} — the same file the
     * plain-LLM chat ({@code TurLLMChatAPI}) injects unconditionally. Loaded
     * once at startup.
     */
    @Value("classpath:prompts/render.md")
    private Resource renderPromptResource;

    private String renderPrompt = "";

    @PostConstruct
    void loadRenderPrompt() {
        try {
            renderPrompt = renderPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[PromptAssembler] could not load prompts/render.md — rich-content "
                    + "agents will fall back to the model's own formatting: {}", e.getMessage());
            renderPrompt = "";
        }
    }

    public TurChatPromptAssembler(TurChatFlowEngineService chatFlowEngineService,
            TurPersonaPromptComposer personaPromptComposer,
            TurChatMemoryRelevanceRetriever chatMemoryRelevanceRetriever,
            TurChatMemoryCompressionService chatMemoryCompressionService,
            TurChatPipelineObservation chatPipelineObservation,
            TurChatAttachmentService chatAttachmentService,
            TurMcpInstructionsProvider mcpInstructionsProvider,
            TurSkillRunnerService skillRunnerService) {
        this.chatFlowEngineService = chatFlowEngineService;
        this.personaPromptComposer = personaPromptComposer;
        this.chatMemoryRelevanceRetriever = chatMemoryRelevanceRetriever;
        this.chatMemoryCompressionService = chatMemoryCompressionService;
        this.chatPipelineObservation = chatPipelineObservation;
        this.chatAttachmentService = chatAttachmentService;
        this.mcpInstructionsProvider = mcpInstructionsProvider;
        this.skillRunnerService = skillRunnerService;
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
        return assemble(agent, history, conversationId, flowContext, effectivePersona,
                lastUserMessage, baseSystemPrompt, null);
    }

    /**
     * Same as {@link #assemble(TurAIAgent, List, String, TurAgentChatFlowContext,
     * TurPersona, String, String)} but additionally attaches the supplied
     * {@code files} as Spring AI {@link org.springframework.ai.content.Media}
     * blocks (images) plus Tika-extracted text on the LAST user message in
     * the post-enrichment history. When {@code files} is null/empty the
     * behaviour is identical to the no-files overload.
     *
     * @param files attachments uploaded with the current turn; null/empty
     *              leaves the user message untouched
     * @since 2026.3.1
     */
    public List<Message> assemble(TurAIAgent agent,
            List<ChatMessageItem> history,
            String conversationId,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona,
            String lastUserMessage,
            String baseSystemPrompt,
            List<MultipartFile> files) {
        return assemble(agent, history, conversationId, flowContext, effectivePersona,
                lastUserMessage, baseSystemPrompt, files, null);
    }

    /**
     * Same as the files overload but with an optional {@code selectedSkillId}
     * (T325). When set, the progressive-disclosure skill block lists only the
     * pinned skill (the "skill mode" of a multi-skill ZIP); when blank/null the
     * full enabled set is listed, exactly as before.
     *
     * @param selectedSkillId nullable skill id/name to pin this turn to a single
     *                        skill; null/blank offers all enabled skills
     * @since 2026.3.1
     */
    public List<Message> assemble(TurAIAgent agent,
            List<ChatMessageItem> history,
            String conversationId,
            TurAgentChatFlowContext flowContext,
            TurPersona effectivePersona,
            String lastUserMessage,
            String baseSystemPrompt,
            List<MultipartFile> files,
            String selectedSkillId) {
        long t0 = System.currentTimeMillis();
        // Append the per-agent MCP server instructions (enabled servers with
        // non-blank llmInstructions) so the model knows how/when to use the
        // tools those servers expose. No-op when the agent has no MCP servers
        // or none carry instructions.
        String mcpAugmented = baseSystemPrompt
                + mcpInstructionsProvider.buildSystemPromptBlock(agent.getMcpServers());
        // Rich-content rendering opt-in: when the operator enabled it on this
        // agent, teach the model the ```html / ```d2 conventions the chat client
        // renders natively. Default-off agents are byte-for-byte unchanged.
        if (agent.isRichContentEnabled() && !renderPrompt.isBlank()) {
            mcpAugmented = mcpAugmented + "\n\n" + renderPrompt;
        }
        // T323 / §IX.4.d — skill delegation. When the operator enabled skills on
        // this agent AND the delegation layer is available (object storage +
        // Code Interpreter DOCKER + a Default LLM), append the cheap
        // progressive-disclosure block (each enabled skill's name + description)
        // and the instruction to call `run_skill`. The skill itself runs on the
        // Global Settings Default LLM (TurSkillRunnerService), never on this
        // agent's model. No-op (empty block) for default-off agents or when the
        // layer is unavailable, so existing agents are byte-for-byte unchanged.
        if (agent.isSkillsEnabled() && skillRunnerService.isAvailable()) {
            // T325 — honour an optional skill-mode pin (single skill) over the
            // full enabled set; offeredSkills(null) returns all (legacy).
            mcpAugmented = mcpAugmented
                    + skillRunnerService.buildSystemPromptBlock(
                            skillRunnerService.offeredSkills(selectedSkillId));
        }
        String flowAugmented = flowContext == null
                ? mcpAugmented
                : mcpAugmented + chatFlowEngineService.buildSystemPromptAddendum(
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

        // T115 / §IX.3.e — workspace-backed memory compression. When the agent
        // has opted in AND the older pool (turns past the recent-N window)
        // exceeds the configured token threshold, prepend a single compact
        // summary of those older turns ahead of the T30-retrieved turns and
        // the recent window. The summary LLM call is throttled to once per
        // configured interval per conversation (cached in the service). No-op
        // when disabled or the pool is small.
        java.util.Optional<ChatMessageItem> summary =
                chatMemoryCompressionService.summaryBlock(agent, conversationId);
        if (summary.isPresent()) {
            List<ChatMessageItem> withSummary = new ArrayList<>(effectiveHistory.size() + 1);
            withSummary.add(summary.get());
            withSummary.addAll(effectiveHistory);
            effectiveHistory = withSummary;
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resolvedSystemPrompt));
        int lastUserIdx = lastUserIndex(effectiveHistory);
        boolean hasFiles = files != null && !files.isEmpty();
        for (int i = 0; i < effectiveHistory.size(); i++) {
            ChatMessageItem item = effectiveHistory.get(i);
            String content = item.content() == null ? "" : item.content();
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(content));
            } else if (hasFiles && i == lastUserIdx) {
                messages.add(chatAttachmentService.buildUserMessageWithFiles(content, files));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    private static int lastUserIndex(List<ChatMessageItem> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if ("user".equals(items.get(i).role())) {
                return i;
            }
        }
        return -1;
    }
}
