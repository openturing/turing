/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.distillation.TurOpenAiDistillationService.DistillResult;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

/**
 * T191 / §X.15.e — unit tests for {@link TurOvernightImprovementJob}: the nightly
 * sweep distills only opted-in agents, skips the rest, and isolates failures.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurOvernightImprovementJobTest {

    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurOpenAiDistillationService distillationService;

    @InjectMocks private TurOvernightImprovementJob job;

    private static TurAIAgent agent(String id, boolean overnight) {
        TurAIAgent a = new TurAIAgent();
        a.setId(id);
        a.setTitle(id);
        a.setOvernightImprovementEnabled(overnight);
        return a;
    }

    @Test
    void distillsOnlyOptedInAgents() {
        when(agentRepository.findAll()).thenReturn(List.of(
                agent("opted-A", true), agent("skip-B", false), agent("opted-C", true)));
        when(distillationService.distillForReview(eq("opted-A")))
                .thenReturn(new DistillResult(true, null, null));
        when(distillationService.distillForReview(eq("opted-C")))
                .thenReturn(new DistillResult(false, "not enough examples", null));

        job.run();

        verify(distillationService).distillForReview("opted-A");
        verify(distillationService).distillForReview("opted-C");
        verify(distillationService, never()).distillForReview("skip-B");
    }

    @Test
    void oneAgentFailureDoesNotAbortTheSweep() {
        when(agentRepository.findAll()).thenReturn(List.of(
                agent("boom", true), agent("ok", true)));
        when(distillationService.distillForReview("boom"))
                .thenThrow(new RuntimeException("OpenAI 500"));
        when(distillationService.distillForReview("ok"))
                .thenReturn(new DistillResult(true, null, null));

        job.run(); // must not throw

        verify(distillationService).distillForReview("boom");
        verify(distillationService).distillForReview("ok");
    }
}
