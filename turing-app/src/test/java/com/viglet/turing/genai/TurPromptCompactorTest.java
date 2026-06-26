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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;

/**
 * Pins the T123 prompt-compaction contract: the leading system message and the
 * recent-N tail are preserved verbatim, the older middle turns are folded into
 * a single summary message, and the no-op paths (nothing older, blank summary)
 * return the original list unchanged.
 */
class TurPromptCompactorTest {

    private TurChatMemoryCompressionService compressionService;
    private TurPromptCompactor compactor;

    @BeforeEach
    void setUp() {
        compressionService = mock(TurChatMemoryCompressionService.class);
        compactor = new TurPromptCompactor(compressionService, new TurChatPipelineObservation(null));
    }

    private static TurAIAgent agent(int recentN) {
        TurAIAgent a = new TurAIAgent();
        a.setId("agent-1");
        a.setChatMemoryRecentN(recentN);
        return a;
    }

    /** [system, u1, a1, u2, a2, u3] with recentN=2 → middle = u1,a1,a2... */
    private static List<Message> conversation() {
        List<Message> m = new ArrayList<>();
        m.add(new SystemMessage("You are a helpful assistant."));
        m.add(new UserMessage("first question"));
        m.add(new AssistantMessage("first answer"));
        m.add(new UserMessage("second question"));
        m.add(new AssistantMessage("second answer"));
        m.add(new UserMessage("current question"));
        return m;
    }

    @Test
    void compactsOlderMiddleKeepsSystemAndRecentTail() {
        when(compressionService.summarizeText(any(), any())).thenReturn("Earlier: user asked things.");

        // recentN=2 → keep last 2 conversation turns (second answer + current question)
        TurPromptCompactor.CompactionResult r = compactor.compact(agent(2), conversation());

        assertThat(r.compacted()).isTrue();
        // 5 conversation turns, keep 2 → 3 summarized
        assertThat(r.summarizedTurns()).isEqualTo(3);

        List<Message> out = r.messages();
        // system + summary + 2 tail = 4
        assertThat(out).hasSize(4);
        assertThat(out.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(out.get(1).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(out.get(1).getText()).contains("Summary of earlier conversation (3 turns)")
                .contains("Earlier: user asked things.");
        // recent tail preserved verbatim, in order
        assertThat(out.get(2).getText()).isEqualTo("second answer");
        assertThat(out.get(3).getText()).isEqualTo("current question");
    }

    @Test
    void noopWhenHistoryWithinRecentWindow() {
        // recentN large enough to cover all 5 conversation turns → nothing older.
        TurPromptCompactor.CompactionResult r = compactor.compact(agent(10), conversation());

        assertThat(r.compacted()).isFalse();
        assertThat(r.summarizedTurns()).isZero();
        verify(compressionService, never()).summarizeText(any(), any());
    }

    @Test
    void noopWhenSummarizerReturnsBlank() {
        when(compressionService.summarizeText(any(), any())).thenReturn("   ");

        TurPromptCompactor.CompactionResult r = compactor.compact(agent(2), conversation());

        assertThat(r.compacted()).isFalse();
        // original list returned unchanged
        assertThat(r.messages()).hasSize(6);
    }

    @Test
    void recentNFlooredToOneKeepsCurrentTurn() {
        when(compressionService.summarizeText(any(), any())).thenReturn("summary");

        // recentN=0 is floored to MIN_RECENT(1) → keep only the current question.
        TurPromptCompactor.CompactionResult r = compactor.compact(agent(0), conversation());

        assertThat(r.compacted()).isTrue();
        assertThat(r.summarizedTurns()).isEqualTo(4);
        List<Message> out = r.messages();
        assertThat(out).hasSize(3); // system + summary + 1 tail
        assertThat(out.get(2).getText()).isEqualTo("current question");
    }

    @Test
    void passesAgentCompressionLlmIdToSummarizer() {
        TurAIAgent a = agent(2);
        a.setChatMemoryCompressionLlmId("llm-cheap");
        when(compressionService.summarizeText(eq("llm-cheap"), any())).thenReturn("summary");

        TurPromptCompactor.CompactionResult r = compactor.compact(a, conversation());

        assertThat(r.compacted()).isTrue();
        verify(compressionService).summarizeText(eq("llm-cheap"), any());
    }

    @Test
    void emptyListIsNoop() {
        TurPromptCompactor.CompactionResult r = compactor.compact(agent(2), List.of());
        assertThat(r.compacted()).isFalse();
        assertThat(r.messages()).isEmpty();
    }
}
