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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.genai.selftuning.TurSelfTuningMiner.FailureSummary;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentSuggestion;
import com.viglet.turing.persistence.repository.agent.TurAgentSuggestionRepository;

@ExtendWith(MockitoExtension.class)
class TurSelfTuningServiceTest {

    @Mock private TurSelfTuningMiner miner;
    @Mock private TurSelfTuningDrafter drafter;
    @Mock private TurAgentEvalRunnerService evalRunner;
    @Mock private TurAgentSuggestionRepository suggestionRepository;

    private TurSelfTuningService service;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        service = new TurSelfTuningService(miner, drafter, evalRunner, suggestionRepository);
        agent = new TurAIAgent();
        agent.setId("a1");
        agent.setSystemPrompt("old prompt");
    }

    @Test
    void skipsWhenEvalUnavailable() {
        when(evalRunner.isAvailable("a1")).thenReturn(false);
        assertThat(service.runCycle(agent)).isEmpty();
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void skipsWhenNoFailures() {
        when(evalRunner.isAvailable("a1")).thenReturn(true);
        when(miner.mine("a1")).thenReturn(new FailureSummary(0, ""));
        assertThat(service.runCycle(agent)).isEmpty();
        verify(drafter, never()).draftSystemPrompt(any(), any());
    }

    @Test
    void skipsWhenDrafterProducesNothing() {
        when(evalRunner.isAvailable("a1")).thenReturn(true);
        when(miner.mine("a1")).thenReturn(new FailureSummary(3, "digest"));
        when(drafter.draftSystemPrompt(eq(agent), any())).thenReturn(Optional.empty());
        assertThat(service.runCycle(agent)).isEmpty();
        verify(evalRunner, never()).scoreWithSystemPrompt(any(), any());
    }

    @Test
    void skipsWhenProposalNotBetter() {
        when(evalRunner.isAvailable("a1")).thenReturn(true);
        when(miner.mine("a1")).thenReturn(new FailureSummary(3, "digest"));
        when(drafter.draftSystemPrompt(eq(agent), any())).thenReturn(Optional.of("new prompt"));
        when(evalRunner.scoreWithSystemPrompt("a1", null)).thenReturn(0.80d);
        when(evalRunner.scoreWithSystemPrompt("a1", "new prompt")).thenReturn(0.80d); // equal → no gain
        assertThat(service.runCycle(agent)).isEmpty();
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    void opensSuggestionWhenProposalBetter() {
        when(evalRunner.isAvailable("a1")).thenReturn(true);
        when(miner.mine("a1")).thenReturn(new FailureSummary(5, "digest"));
        when(drafter.draftSystemPrompt(eq(agent), any())).thenReturn(Optional.of("better prompt"));
        when(evalRunner.scoreWithSystemPrompt("a1", null)).thenReturn(0.70d);
        when(evalRunner.scoreWithSystemPrompt("a1", "better prompt")).thenReturn(0.90d);
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<TurAgentSuggestion> out = service.runCycle(agent);
        assertThat(out).isPresent();
        TurAgentSuggestion s = out.get();
        assertThat(s.getAgentId()).isEqualTo("a1");
        assertThat(s.getKind()).isEqualTo(TurAgentSuggestion.Kind.SYSTEM_PROMPT);
        assertThat(s.getCurrentValue()).isEqualTo("old prompt");
        assertThat(s.getProposedValue()).isEqualTo("better prompt");
        assertThat(s.getBaselineScore()).isEqualTo(0.70d);
        assertThat(s.getProposedScore()).isEqualTo(0.90d);
        assertThat(s.getStatus()).isEqualTo(TurAgentSuggestion.Status.PENDING);
        verify(suggestionRepository).save(any());
    }

    @Test
    void skipsWhenBaselineUnavailable() {
        when(evalRunner.isAvailable("a1")).thenReturn(true);
        when(miner.mine("a1")).thenReturn(new FailureSummary(2, "digest"));
        when(drafter.draftSystemPrompt(eq(agent), any())).thenReturn(Optional.of("new prompt"));
        when(evalRunner.scoreWithSystemPrompt("a1", null))
                .thenReturn(TurAgentEvalRunnerService.SCORE_UNAVAILABLE);
        assertThat(service.runCycle(agent)).isEmpty();
        verify(suggestionRepository, never()).save(any());
    }
}
