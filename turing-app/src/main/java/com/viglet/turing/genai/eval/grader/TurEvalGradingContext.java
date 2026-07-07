/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;

import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;

/**
 * T586 / §XXXIII.1 — everything a {@link TurEvalGrader} needs to score one
 * replayed eval case. Assembled once per case by the eval runner after the
 * conversation replay finishes.
 *
 * @param evalCase         the source golden case (its expectations + rubric)
 * @param conversationId   the replay conversation id (T588: lets CODE graders
 *                         read the T427 tool-call trace for this case)
 * @param userTurns        the scripted user turns that were replayed, in order
 * @param assistantReplies the assistant reply produced for each user turn (same
 *                         cardinality as {@code userTurns}); enables CODE graders
 *                         that assert on the final / full answer (T588)
 * @param capturedSlots    the slots the replay captured ({@code slot -> value})
 * @param finalNodeId      the flow cursor the replay landed on (nullable)
 * @param actualOutcome    the terminal outcome observed after the replay
 * @param judgeModel       the resolved LLM used by MODEL graders (nullable when
 *                         no usable LLM — CODE graders still run)
 * @param referenceAnswer  T595 — the bound dataset row's golden reference answer
 *                         (nullable; reference graders fall back to their config)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalGradingContext(
        TurAgentEvalCase evalCase,
        String conversationId,
        List<String> userTurns,
        List<String> assistantReplies,
        Map<String, String> capturedSlots,
        String finalNodeId,
        String actualOutcome,
        ChatModel judgeModel,
        String referenceAnswer) {

    /** Legacy 8-arg constructor (no dataset reference); {@code referenceAnswer=null}. */
    public TurEvalGradingContext(TurAgentEvalCase evalCase, String conversationId,
            List<String> userTurns, List<String> assistantReplies, Map<String, String> capturedSlots,
            String finalNodeId, String actualOutcome, ChatModel judgeModel) {
        this(evalCase, conversationId, userTurns, assistantReplies, capturedSlots, finalNodeId,
                actualOutcome, judgeModel, null);
    }

    /** The last assistant reply (the "final answer"), or {@code ""} when none. */
    public String finalAnswer() {
        return assistantReplies == null || assistantReplies.isEmpty()
                ? ""
                : assistantReplies.get(assistantReplies.size() - 1);
    }
}
