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
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.prompt.TurMessageAssemblyContext;
import com.viglet.turing.genai.prompt.TurMessageAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptAssembly;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptMessage;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Composes the {@code List<Message>} that ships to the LLM:
 * <ol>
 *   <li>Builds the system prompt through the single-pass
 *       {@link com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline}
 *       contributor chain (persona voice + constraints + brand-context, agent /
 *       RAG-override base, MCP, opt-in capabilities, skills, flow addendum) — the
 *       same segments the Live Preview renders.</li>
 *   <li>Builds the history-prefix (T115 memory summary + T30 relevance-retrieved
 *       older turns) through the {@code TurMessageAssemblyPipeline} contributor
 *       chain (T616) and prepends it to the client history.</li>
 *   <li>Builds the ordered {@code List<Message>} (system + history fan-out
 *       into {@code UserMessage}/{@code AssistantMessage}).</li>
 * </ol>
 *
 * <p>Extracted from {@link TurAgentChatExecutor} in T33. Owns the
 * {@link TurMeterNames#STAGE_CHAT_SETUP_COMPOSE} substage timing — measured
 * around the system-prompt build (matches the pre-T33 boundary so dashboards
 * keep reading the same number). T615 retired the pre-pipeline inline
 * system-prompt concatenation and its
 * {@code turing.prompt.assembly.pipeline-enabled} escape hatch; T617 did the same
 * for the history-prefix messages, deleting the inline {@code buildMemoryPrefixLegacy}
 * path and its {@code turing.prompt.assembly.message-pipeline-enabled} flag. Both
 * the system message and the history prefix now have a single assembly path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatPromptAssembler {

    private final TurChatPipelineObservation chatPipelineObservation;
    private final TurChatAttachmentService chatAttachmentService;
    private final TurPromptAssemblyPipeline promptAssemblyPipeline;
    private final TurMessageAssemblyPipeline messageAssemblyPipeline;

    public TurChatPromptAssembler(TurChatPipelineObservation chatPipelineObservation,
            TurChatAttachmentService chatAttachmentService,
            TurPromptAssemblyPipeline promptAssemblyPipeline,
            TurMessageAssemblyPipeline messageAssemblyPipeline) {
        this.chatPipelineObservation = chatPipelineObservation;
        this.chatAttachmentService = chatAttachmentService;
        this.promptAssemblyPipeline = promptAssemblyPipeline;
        this.messageAssemblyPipeline = messageAssemblyPipeline;
    }

    /**
     * Block AL / T613 + T615 — build the system prompt through the single-pass
     * {@link TurPromptAssemblyPipeline}. The contributor chain
     * ({@code persona → agent-base → MCP → capability → skill → flow}) produces
     * the same labeled segments the Live Preview renders, so runtime and preview
     * can never drift. This is now the <em>only</em> assembly path: T615 retired
     * the pre-pipeline inline concatenation and its
     * {@code turing.prompt.assembly.pipeline-enabled} escape hatch once the
     * pipeline had soaked in production. Every prompt source (persona, agent base,
     * MCP, each opt-in capability block, skills, flow) owns its text in its own
     * {@code TurPromptContributor}; adding a new source is "register a
     * contributor", never "edit this class plus a parallel rebuild".
     */
    private String buildSystemPrompt(TurChatPromptRequest request, String selectedSkillId) {
        TurAIAgent agent = request.agent();
        TurPromptAssemblyContext context = new TurPromptAssemblyContext(
                agent,
                request.effectivePersona(),
                agent.getTurEmbeddingModelInstance(),
                request.lastUserMessage(),
                request.baseSystemPrompt(),
                request.flowContext(),
                selectedSkillId);
        TurPromptAssembly assembly = promptAssemblyPipeline.assemble(context);
        return assembly.systemText();
    }

    /**
     * Block AL / §XXXV.3 (T616 + T617) — build the history-prefix messages (the
     * T115 chat-memory compression summary followed by the T30 relevance-retrieved
     * older turns) that sit between the system message and the client's recent
     * window.
     *
     * <p>Runs the {@link TurMessageAssemblyPipeline} contributor chain, so the two
     * sources — historically concatenated inline here — live behind the same
     * composable SPI as the system prompt. T617 deleted the inline
     * {@code buildMemoryPrefixLegacy} escape hatch: this is now the single path.
     * Returns an empty list when neither source contributes.
     */
    private List<ChatMessageItem> buildMemoryPrefix(TurAIAgent agent, String conversationId,
            String lastUserMessage) {
        List<TurPromptMessage> prefix = messageAssemblyPipeline.assemble(
                new TurMessageAssemblyContext(agent, conversationId, lastUserMessage));
        if (prefix.isEmpty()) {
            return List.of();
        }
        List<ChatMessageItem> items = new ArrayList<>(prefix.size());
        for (TurPromptMessage m : prefix) {
            items.add(new ChatMessageItem(m.role(), m.content()));
        }
        return items;
    }

    /**
     * Produce the final {@code List<Message>} for the LLM call. See
     * {@link TurChatPromptRequest} for the meaning of each bundled input.
     */
    public List<Message> assemble(TurChatPromptRequest request) {
        return assemble(request, null);
    }

    /**
     * Same as {@link #assemble(TurChatPromptRequest)} but additionally attaches
     * the supplied
     * {@code files} as Spring AI {@link org.springframework.ai.content.Media}
     * blocks (images) plus Tika-extracted text on the LAST user message in
     * the post-enrichment history. When {@code files} is null/empty the
     * behaviour is identical to the no-files overload.
     *
     * @param files attachments uploaded with the current turn; null/empty
     *              leaves the user message untouched
     * @since 2026.3.1
     */
    public List<Message> assemble(TurChatPromptRequest request, List<MultipartFile> files) {
        return assemble(request, files, null);
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
    public List<Message> assemble(TurChatPromptRequest request, List<MultipartFile> files,
            String selectedSkillId) {
        TurAIAgent agent = request.agent();
        List<ChatMessageItem> history = request.history();
        String conversationId = request.conversationId();
        String lastUserMessage = request.lastUserMessage();
        long t0 = System.currentTimeMillis();
        String resolvedSystemPrompt = buildSystemPrompt(request, selectedSkillId);
        long elapsed = System.currentTimeMillis() - t0;
        chatPipelineObservation.recordMillis(TurMeterNames.STAGE_CHAT_SETUP_COMPOSE, elapsed);

        // T616 / §XXXV.3 — build the history-prefix (T115 memory summary + T30
        // relevance-retrieved older turns) through the message contributor pipeline,
        // then prepend it to the client history. The whole message list now has one
        // assembly path. Prefix ordering is [summary] + [retrieved], identical to
        // the legacy inline layering the escape hatch restores.
        List<ChatMessageItem> memoryPrefix = buildMemoryPrefix(agent, conversationId, lastUserMessage);
        List<ChatMessageItem> effectiveHistory;
        if (memoryPrefix.isEmpty()) {
            effectiveHistory = history;
        } else {
            effectiveHistory = new ArrayList<>(memoryPrefix.size() + history.size());
            effectiveHistory.addAll(memoryPrefix);
            effectiveHistory.addAll(history);
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
