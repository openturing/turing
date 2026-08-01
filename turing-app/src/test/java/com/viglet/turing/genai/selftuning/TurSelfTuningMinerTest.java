/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.selftuning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.answer.TurAnswerSignal;
import com.viglet.turing.persistence.model.answer.TurAnswerSignalEvent;
import com.viglet.turing.persistence.repository.answer.TurAnswerSignalRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatanalytics.TurChatSessionFilter;

/**
 * T845 / §LXII.14 — coverage for {@link TurSelfTuningMiner} folding answer-level
 * relevance-miss signals into the failure summary alongside chat-session failures.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSelfTuningMinerTest {

    @Mock
    private TurChatAnalyticsStore analyticsStore;
    @Mock
    private TurAnswerSignalRepository answerSignalRepository;

    @InjectMocks
    private TurSelfTuningMiner miner;

    private TurAnswerSignalEvent miss(TurAnswerSignal signal, String query) {
        TurAnswerSignalEvent e = new TurAnswerSignalEvent();
        e.setSignal(signal);
        e.setQuery(query);
        e.setRelevanceMiss(true);
        return e;
    }

    @Test
    void mineFoldsAnswerRelevanceMissesIntoTheSummary() {
        when(analyticsStore.findRecentSessions(any(), any(), any(TurChatSessionFilter.class), anyInt()))
                .thenReturn(List.of(Map.of("outcome", "ABANDONED", "totalTurns", 3)));
        when(answerSignalRepository
                .findByAgentIdAndRelevanceMissTrueAndCreatedAtAfterOrderByCreatedAtDesc(eq("agent-1"), any(Instant.class)))
                .thenReturn(List.of(
                        miss(TurAnswerSignal.FACET_REFINED_AFTER_ANSWER, "cheapest model?"),
                        miss(TurAnswerSignal.THUMBS_DOWN, "fastest model?")));

        TurSelfTuningMiner.FailureSummary summary = miner.mine("agent-1");

        // One failing session + two answer misses.
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.digest())
                .contains("outcome=ABANDONED")
                .contains("answerSignal=FACET_REFINED_AFTER_ANSWER")
                .contains("query=cheapest model?")
                .contains("answerSignal=THUMBS_DOWN");
    }

    @Test
    void mineIsEmptyWhenNeitherSourceHasFailures() {
        when(analyticsStore.findRecentSessions(any(), any(), any(TurChatSessionFilter.class), anyInt()))
                .thenReturn(List.of(Map.of("outcome", "COMPLETED")));
        when(answerSignalRepository
                .findByAgentIdAndRelevanceMissTrueAndCreatedAtAfterOrderByCreatedAtDesc(eq("agent-1"), any(Instant.class)))
                .thenReturn(List.of());

        TurSelfTuningMiner.FailureSummary summary = miner.mine("agent-1");

        assertThat(summary.count()).isZero();
        assertThat(summary.isEmpty()).isTrue();
    }

    @Test
    void answerSignalRepositoryErrorDegradesFailOpen() {
        when(analyticsStore.findRecentSessions(any(), any(), any(TurChatSessionFilter.class), anyInt()))
                .thenReturn(List.of());
        when(answerSignalRepository
                .findByAgentIdAndRelevanceMissTrueAndCreatedAtAfterOrderByCreatedAtDesc(eq("agent-1"), any(Instant.class)))
                .thenThrow(new RuntimeException("db down"));

        TurSelfTuningMiner.FailureSummary summary = miner.mine("agent-1");

        assertThat(summary.count()).isZero();
    }
}
