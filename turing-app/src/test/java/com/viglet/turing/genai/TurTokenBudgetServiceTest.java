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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentOverBudgetBehavior;

/**
 * Pins the T123 token-budget contract: budget=0 disables the check,
 * estimator follows chars/4 with ceiling, the three over-budget
 * behaviours map to WITHIN_BUDGET / WARN / ERROR correctly, and the
 * COMPACT v1 mode degrades to WARN with a logged notice.
 */
class TurTokenBudgetServiceTest {

    private final TurTokenBudgetService service = new TurTokenBudgetService();

    private static TurAIAgent agent(int max, TurAgentOverBudgetBehavior behavior) {
        TurAIAgent a = new TurAIAgent();
        a.setId("agent-1");
        a.setMaxPromptTokens(max);
        a.setOverBudgetBehavior(behavior);
        return a;
    }

    private static List<Message> messages(String... texts) {
        return List.of((Message[]) java.util.stream.Stream.of(texts)
                .map(t -> t.startsWith("sys:")
                        ? (Message) new SystemMessage(t.substring(4))
                        : (Message) new UserMessage(t))
                .toArray(Message[]::new));
    }

    @Test
    void disabledBudgetSkipsEstimate() {
        TurTokenBudgetService.CheckResult r = service.check(agent(0, TurAgentOverBudgetBehavior.WARN),
                messages("hello world"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WITHIN_BUDGET);
        assertThat(r.estimatedTokens()).isZero();
        assertThat(r.budget()).isZero();
    }

    @Test
    void underBudgetReturnsWithinBudget() {
        // "hello" = 5 chars → ceil(5/4) = 2 tokens; budget=10 → within.
        TurTokenBudgetService.CheckResult r = service.check(agent(10, TurAgentOverBudgetBehavior.ERROR),
                messages("hello"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WITHIN_BUDGET);
        assertThat(r.estimatedTokens()).isEqualTo(2);
        assertThat(r.budget()).isEqualTo(10);
    }

    @Test
    void exactBudgetIsWithin() {
        // 8 chars → 2 tokens, budget=2 → within (uses <=)
        TurTokenBudgetService.CheckResult r = service.check(agent(2, TurAgentOverBudgetBehavior.ERROR),
                messages("12345678"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WITHIN_BUDGET);
    }

    @Test
    void overBudgetErrorReturnsError() {
        TurTokenBudgetService.CheckResult r = service.check(agent(1, TurAgentOverBudgetBehavior.ERROR),
                messages("this is a longer string with more chars"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.ERROR);
        assertThat(r.estimatedTokens()).isGreaterThan(1);
    }

    @Test
    void overBudgetWarnReturnsWarn() {
        TurTokenBudgetService.CheckResult r = service.check(agent(1, TurAgentOverBudgetBehavior.WARN),
                messages("this is too long"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WARN);
    }

    @Test
    void overBudgetCompactDegradesToWarnInV1() {
        TurTokenBudgetService.CheckResult r = service.check(agent(1, TurAgentOverBudgetBehavior.COMPACT),
                messages("this is too long"));
        // V1: COMPACT not yet implemented — logged + behave as WARN.
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WARN);
    }

    @Test
    void nullBehaviorDefaultsToWarn() {
        TurAIAgent a = agent(1, null);
        TurTokenBudgetService.CheckResult r = service.check(a,
                messages("over the budget for sure"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WARN);
    }

    @Test
    void estimateSumsAcrossMessages() {
        // sys: "long context here" (17 chars) + user: "hi" (2 chars) =
        // 19 chars total → ceil(19/4) = 5 tokens.
        int tokens = TurTokenBudgetService.estimateTokens(messages("sys:long context here", "hi"));
        assertThat(tokens).isEqualTo(5);
    }

    @Test
    void estimateHandlesEmptyAndNull() {
        assertThat(TurTokenBudgetService.estimateTokens(null)).isZero();
        assertThat(TurTokenBudgetService.estimateTokens(List.of())).isZero();
    }

    @Test
    void estimateCeilingPreventsZeroTokenMessage() {
        // 1 char → ceil(1/4) = 1 token (not 0).
        assertThat(TurTokenBudgetService.estimateTokens(messages("a"))).isEqualTo(1);
    }

    @Test
    void nullAgentSkipsCheck() {
        TurTokenBudgetService.CheckResult r = service.check(null, messages("anything"));
        assertThat(r.decision()).isEqualTo(TurTokenBudgetService.Decision.WITHIN_BUDGET);
    }
}
