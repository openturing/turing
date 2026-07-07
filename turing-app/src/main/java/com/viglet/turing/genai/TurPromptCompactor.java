/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;

import lombok.extern.slf4j.Slf4j;

/**
 * T123 / §IX.7 — budget-driven prompt compaction.
 *
 * <p>When the assembled prompt for a turn exceeds the agent's
 * {@code maxPromptTokens} and the agent's
 * {@link com.viglet.turing.persistence.model.agent.TurAgentOverBudgetBehavior}
 * is {@code COMPACT}, the {@link TurAgentChatExecutor} routes the
 * {@code List<Message>} through this compactor BEFORE the LLM call. The
 * compactor:
 * <ol>
 *   <li>keeps the leading system message(s) verbatim,</li>
 *   <li>keeps the most recent {@code chatMemoryRecentN} conversation turns
 *       verbatim (so the current user turn — with any attachments — and its
 *       immediate context are never altered), and</li>
 *   <li>replaces the older middle turns with a single LLM-generated summary
 *       message produced by {@link TurChatMemoryCompressionService#summarizeText}
 *       (the same summarizer T115 uses for the scheduled older-pool summary).</li>
 * </ol>
 *
 * <p>This operates on the in-prompt {@code List<Message>} (the conversation the
 * client actually sent this turn), which is the lever that reduces the
 * predicted token count — distinct from T115's persisted-store summary, which
 * is <em>added</em> to the prompt. Reusing T115's summarizer keeps a single
 * compression prompt and LLM-instance resolution.
 *
 * <h2>No-op (pass-through) conditions</h2>
 * Returns the original list unchanged when there are no older middle turns to
 * summarize (history already within the recent window) or the summarizer
 * returns blank (no usable LLM, call error) — so a compaction failure never
 * breaks the turn; the executor proceeds best-effort (logged) even if still
 * over budget.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurPromptCompactor {

    /** Floor on the recent window kept verbatim — always preserve the last turn. */
    static final int MIN_RECENT = 1;

    private final TurChatMemoryCompressionService compressionService;
    private final TurChatPipelineObservation chatPipelineObservation;

    public TurPromptCompactor(TurChatMemoryCompressionService compressionService,
            TurChatPipelineObservation chatPipelineObservation) {
        this.compressionService = compressionService;
        this.chatPipelineObservation = chatPipelineObservation;
    }

    /**
     * Outcome of a compaction attempt.
     *
     * @param messages        the (possibly unchanged) message list to send.
     * @param compacted       true when older turns were summarized.
     * @param summarizedTurns number of middle turns folded into the summary.
     * @param beforeTokens    estimated tokens of the input list.
     * @param afterTokens     estimated tokens of {@link #messages} (== before on a no-op).
     */
    public record CompactionResult(List<Message> messages, boolean compacted, int summarizedTurns,
            int beforeTokens, int afterTokens) {
    }

    /**
     * Summarize the older middle turns of {@code messages} into one block,
     * keeping the leading system message(s) and the recent-N tail verbatim.
     *
     * @param agent    the chatting agent — read for {@code chatMemoryRecentN}
     *                 and {@code chatMemoryCompressionLlmId}.
     * @param messages the assembled prompt for this turn.
     * @return a {@link CompactionResult}; on any no-op path it carries the
     *         original list with {@code compacted=false}.
     */
    public CompactionResult compact(TurAIAgent agent, List<Message> messages) {
        int before = TurTokenBudgetService.estimateTokens(messages);
        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(messages, false, 0, before, before);
        }

        // Leading system message(s) stay at the front, untouched.
        int systemEnd = 0;
        while (systemEnd < messages.size()
                && messages.get(systemEnd).getMessageType() == MessageType.SYSTEM) {
            systemEnd++;
        }
        List<Message> systemPrefix = messages.subList(0, systemEnd);
        List<Message> conversation = messages.subList(systemEnd, messages.size());

        int recentN = Math.max(MIN_RECENT, agent.getChatMemoryRecentN());
        int middleEnd = conversation.size() - recentN;
        if (middleEnd <= 0) {
            // History already within the recent window — nothing older to fold.
            return new CompactionResult(messages, false, 0, before, before);
        }

        List<Message> middle = conversation.subList(0, middleEnd);
        List<Message> recentTail = conversation.subList(middleEnd, conversation.size());

        String renderedMiddle = renderTurns(middle);
        String summary = compressionService.summarizeText(
                agent.getChatMemoryCompressionLlmId(), renderedMiddle);
        if (summary == null || summary.isBlank()) {
            log.warn("[PromptCompactor] agent='{}' summarizer returned blank for {} older turns — "
                    + "leaving prompt uncompacted", agent.getId(), middle.size());
            return new CompactionResult(messages, false, 0, before, before);
        }

        List<Message> compacted = new ArrayList<>(systemPrefix.size() + 1 + recentTail.size());
        compacted.addAll(systemPrefix);
        compacted.add(new UserMessage(
                "[Summary of earlier conversation (" + middle.size() + " turns)]\n" + summary));
        compacted.addAll(recentTail);

        int after = TurTokenBudgetService.estimateTokens(compacted);
        // T124 — compressed/original char ratio of the folded middle.
        chatPipelineObservation.recordCompressionRatio(renderedMiddle.length(), summary.length());
        log.info("[PromptCompactor] agent='{}' summarized {} older turns ({} → {} est tokens)",
                agent.getId(), middle.size(), before, after);
        return new CompactionResult(compacted, true, middle.size(), before, after);
    }

    /** Renders a run of messages to {@code [role]: text} lines for the summarizer. */
    private static String renderTurns(List<Message> turns) {
        StringBuilder sb = new StringBuilder(turns.size() * 64);
        for (Message m : turns) {
            String text = m.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append('[').append(roleOf(m)).append("]: ").append(text).append('\n');
        }
        return sb.toString();
    }

    private static String roleOf(Message m) {
        return m.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user";
    }
}
