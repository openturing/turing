/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T89 / §VII.10.f — unit coverage for the in-memory router-decision ring and
 * the structured log line format. The buffer logic is exercised by
 * instantiating the service directly (no Spring), so the static
 * {@code getInstance()} singleton stays {@code null} and
 * {@link TurChatFlowRouterDecisionLog#recordSafely} degrades to a no-op —
 * itself one of the assertions below.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowRouterDecisionLogTest {

    private static TurChatFlowRouterDecision decision(String conversationId,
            String winnerId, TurChatFlowRouterMethod method) {
        return new TurChatFlowRouterDecision(1L, "agent-1", conversationId, "quero férias",
                method, winnerId, winnerId,
                List.of(new TurChatFlowRouterDecision.Candidate(winnerId, winnerId, 4.2f, true),
                        new TurChatFlowRouterDecision.Candidate("loser", "loser", 1.1f, false)),
                4.2f, 1.1f, 4.2f / 1.1f, true, null);
    }

    @Test
    void retainsAndReturnsNewestFirst() {
        var log = new TurChatFlowRouterDecisionLog();
        log.record(decision("conv-1", "a", TurChatFlowRouterMethod.PROCEDURAL));
        log.record(decision("conv-1", "b", TurChatFlowRouterMethod.LLM));

        List<TurChatFlowRouterDecision> recent = log.recentForConversation("conv-1", 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).winnerFlowId()).isEqualTo("b");
        assertThat(recent.get(1).winnerFlowId()).isEqualTo("a");
    }

    @Test
    void perConversationRingIsCapped() {
        var log = new TurChatFlowRouterDecisionLog();
        int total = TurChatFlowRouterDecisionLog.MAX_PER_CONVERSATION + 5;
        for (int i = 0; i < total; i++) {
            log.record(decision("conv-1", "flow-" + i, TurChatFlowRouterMethod.PROCEDURAL));
        }
        List<TurChatFlowRouterDecision> recent = log.recentForConversation("conv-1", 1000);
        assertThat(recent).hasSize(TurChatFlowRouterDecisionLog.MAX_PER_CONVERSATION);
        // Newest first, oldest five evicted.
        assertThat(recent.get(0).winnerFlowId()).isEqualTo("flow-" + (total - 1));
        assertThat(recent).noneMatch(d -> d.winnerFlowId().equals("flow-0"));
    }

    @Test
    void globalRingIsCappedAndNewestFirst() {
        var log = new TurChatFlowRouterDecisionLog();
        int total = TurChatFlowRouterDecisionLog.MAX_GLOBAL + 10;
        for (int i = 0; i < total; i++) {
            log.record(decision("conv-" + i, "flow-" + i, TurChatFlowRouterMethod.LLM));
        }
        List<TurChatFlowRouterDecision> recent = log.recent(0);
        assertThat(recent).hasSize(TurChatFlowRouterDecisionLog.MAX_GLOBAL);
        assertThat(recent.get(0).winnerFlowId()).isEqualTo("flow-" + (total - 1));
    }

    @Test
    void recentForConversationIsScopedToThatConversation() {
        var log = new TurChatFlowRouterDecisionLog();
        log.record(decision("conv-1", "a", TurChatFlowRouterMethod.PROCEDURAL));
        log.record(decision("conv-2", "b", TurChatFlowRouterMethod.LLM));

        assertThat(log.recentForConversation("conv-1", 10))
                .extracting(TurChatFlowRouterDecision::winnerFlowId)
                .containsExactly("a");
        assertThat(log.recentForConversation("unknown", 10)).isEmpty();
        assertThat(log.recentForConversation(null, 10)).isEmpty();
        assertThat(log.recentForConversation("  ", 10)).isEmpty();
    }

    @Test
    void limitCapsResults() {
        var log = new TurChatFlowRouterDecisionLog();
        for (int i = 0; i < 5; i++) {
            log.record(decision("conv-1", "flow-" + i, TurChatFlowRouterMethod.PROCEDURAL));
        }
        assertThat(log.recentForConversation("conv-1", 2)).hasSize(2);
        assertThat(log.recent(3)).hasSize(3);
    }

    @Test
    void blankConversationStaysOutOfPerConversationRingButCountsGlobally() {
        var log = new TurChatFlowRouterDecisionLog();
        log.record(decision("", "a", TurChatFlowRouterMethod.NONE));
        assertThat(log.recentForConversation("", 10)).isEmpty();
        assertThat(log.recent(10)).hasSize(1);
    }

    @Test
    void distinctConversationRingIsLruBounded() {
        var log = new TurChatFlowRouterDecisionLog();
        int total = TurChatFlowRouterDecisionLog.MAX_CONVERSATIONS + 3;
        for (int i = 0; i < total; i++) {
            log.record(decision("conv-" + i, "flow", TurChatFlowRouterMethod.PROCEDURAL));
        }
        // The three eldest conversations were evicted; the newest survives.
        assertThat(log.recentForConversation("conv-0", 10)).isEmpty();
        assertThat(log.recentForConversation("conv-" + (total - 1), 10)).hasSize(1);
    }

    @Test
    void recordSafelyIsNoOpWhenNoSingletonRegistered() throws Exception {
        // recordSafely must, when no singleton is registered, neither throw nor
        // populate the freshly-built (unregistered) log. We force the static
        // singleton to null for the duration of the assertion rather than
        // assuming it: a cached @SpringBootTest context in the same Surefire
        // fork registers this @Service via @PostConstruct and never tears it
        // down (Spring caches contexts), so getInstance() is otherwise
        // order-dependent. Restore the prior value afterwards.
        Field instanceField = TurChatFlowRouterDecisionLog.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object previous = instanceField.get(null);
        instanceField.set(null, null);
        try {
            var log = new TurChatFlowRouterDecisionLog();
            assertThat(TurChatFlowRouterDecisionLog.getInstance()).isNull();
            assertThatCode(() -> TurChatFlowRouterDecisionLog
                    .recordSafely(decision("conv-1", "a", TurChatFlowRouterMethod.LLM)))
                    .doesNotThrowAnyException();
            assertThat(log.recent(10)).isEmpty();
        } finally {
            instanceField.set(null, previous);
        }
    }

    @Test
    void nullDecisionIsIgnored() {
        var log = new TurChatFlowRouterDecisionLog();
        assertThatCode(() -> log.record(null)).doesNotThrowAnyException();
        assertThat(log.recent(10)).isEmpty();
    }

    @Test
    void formatRendersWinnerScoresAndMethod() {
        String rendered = TurChatFlowRouterDecisionLog.format(
                decision("conv-1", "ferias", TurChatFlowRouterMethod.PROCEDURAL));
        assertThat(rendered)
                .contains("method=PROCEDURAL")
                .contains("winner=ferias")
                .contains("*ferias:4.200")
                .contains("loser:1.100")
                .contains("best=4.200")
                .contains("ratio=3.818")
                .contains("msg=\"quero férias\"");
    }

    @Test
    void formatOmitsProceduralScoresForLlmDecision() {
        var llm = new TurChatFlowRouterDecision(1L, "agent-1", "conv-1", "oi",
                TurChatFlowRouterMethod.LLM, "ferias", "ferias",
                List.of(new TurChatFlowRouterDecision.Candidate("ferias", "ferias", null, true)),
                null, null, null, false, null);
        String rendered = TurChatFlowRouterDecisionLog.format(llm);
        assertThat(rendered)
                .contains("method=LLM")
                .contains("ferias:-")
                .doesNotContain("best=");
    }

    @Test
    void userMessageIsTruncatedToCap() {
        String longMessage = "x".repeat(TurChatFlowRouterDecision.MAX_MESSAGE_LENGTH + 50);
        var d = new TurChatFlowRouterDecision(1L, "agent-1", "conv-1", longMessage,
                TurChatFlowRouterMethod.LLM, null, null, List.of(),
                null, null, null, false, null);
        assertThat(d.userMessage())
                .hasSize(TurChatFlowRouterDecision.MAX_MESSAGE_LENGTH + 1) // + the ellipsis char
                .endsWith("…");
    }
}
