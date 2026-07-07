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
package com.viglet.turing.genai.distillation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.distillation.TurDpoDatasetBuilder.FeedbackEntry;
import com.viglet.turing.genai.distillation.TurDpoDatasetBuilder.PreferencePair;
import com.viglet.turing.persistence.model.distillation.TurChatTurnFeedback;
import com.viglet.turing.persistence.repository.distillation.TurChatTurnFeedbackRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * F.9 / §X.10.d — T170. Records operator thumb-up/down feedback on assistant
 * turns and builds the incremental DPO preference dataset from it.
 *
 * <p>This closes the self-improvement flywheel's preference half: operators rate
 * answers as they review chats, and {@link #buildDpoDataset} pairs contradictory
 * ratings (chosen up vs rejected down for the same prompt) into an OpenAI DPO
 * dataset — fed to a {@code method:dpo} fine-tune the same way T169 feeds a
 * supervised fine-tune from Stored Completions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurChatFeedbackService {

    private final TurChatTurnFeedbackRepository feedbackRepository;

    public TurChatFeedbackService(TurChatTurnFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /** A built DPO dataset: the preference pairs + the OpenAI-format JSONL. */
    public record DpoDataset(int pairCount, int feedbackCount, String jsonl) {
    }

    /** Persist one operator rating; blank prompt/answer is rejected (nothing to learn from). */
    public TurChatTurnFeedback record(String agentId, String conversationId, String prompt,
            String answer, TurChatFeedbackRating rating) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(prompt)
                || !StringUtils.hasText(answer) || rating == null) {
            throw new IllegalArgumentException("agentId, prompt, answer and rating are required");
        }
        TurChatTurnFeedback feedback = new TurChatTurnFeedback();
        feedback.setAgentId(agentId);
        feedback.setConversationId(conversationId);
        feedback.setUserPrompt(prompt);
        feedback.setAssistantAnswer(answer);
        feedback.setRating(rating);
        TurChatTurnFeedback saved = feedbackRepository.save(feedback);
        log.debug("[DPO] recorded {} feedback for agent '{}' (conv '{}')",
                rating, agentId, conversationId);
        return saved;
    }

    /** Build the agent's incremental DPO dataset from all recorded feedback. */
    public DpoDataset buildDpoDataset(String agentId) {
        List<TurChatTurnFeedback> rows = feedbackRepository.findByAgentIdOrderByCreatedAtAsc(agentId);
        List<FeedbackEntry> entries = rows.stream()
                .map(r -> new FeedbackEntry(r.getUserPrompt(), r.getAssistantAnswer(),
                        r.getRating() == TurChatFeedbackRating.UP))
                .toList();
        List<PreferencePair> pairs = TurDpoDatasetBuilder.pair(entries);
        String jsonl = TurDpoDatasetBuilder.build(entries);
        return new DpoDataset(pairs.size(), rows.size(), jsonl);
    }
}
