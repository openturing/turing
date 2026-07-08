/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.dto.agent.TurEvalRunRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalRunResultDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalGraderStack;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository;

/**
 * T602 / §XXXIII.17 — unit coverage for the public {@code POST /api/eval/run}
 * gate: dataset / grader-stack resolution (id-first, then name), the CI gate
 * verdict (every case passes AND optional {@code minScore}), the default-stack
 * shortcut, and the error mappings (400 / 404 / 422). No Spring context, no LLM
 * — the runner is mocked.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalRunAPITest {

    private static final String AGENT_ID = "agent-1";

    @Mock
    private TurAgentEvalRunnerService runnerService;
    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalGraderStackRepository graderStackRepository;

    @InjectMocks
    private TurEvalRunAPI api;

    private TurEvalDataset dataset;

    @BeforeEach
    void setUp() {
        dataset = new TurEvalDataset();
        dataset.setId("ds-1");
        dataset.setName("Golden Leads");
    }

    private TurAgentEvalReportDto report(boolean passed, double score) {
        return new TurAgentEvalReportDto("rep-1", LocalDateTime.now(), passed, score, 3,
                passed ? 3 : 2, false, false, false, "ds-1", 1, List.of(), null);
    }

    @Test
    void resolvesDatasetById_defaultStack_andPasses() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(report(true, 0.9));

        TurEvalRunResultDto result = api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", null, null));

        assertThat(result.gatePassed()).isTrue();
        assertThat(result.datasetId()).isEqualTo("ds-1");
        assertThat(result.datasetName()).isEqualTo("Golden Leads");
        assertThat(result.graderStackId()).isNull();
    }

    @Test
    void resolvesDatasetByName_whenIdMisses() {
        when(datasetRepository.findById("Golden Leads")).thenReturn(Optional.empty());
        when(datasetRepository.findFirstByNameOrderByCreatedAtDesc("Golden Leads"))
                .thenReturn(Optional.of(dataset));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(report(true, 1.0));

        TurEvalRunResultDto result = api.run(new TurEvalRunRequest(AGENT_ID, "Golden Leads", null, null));

        assertThat(result.gatePassed()).isTrue();
        assertThat(result.datasetId()).isEqualTo("ds-1");
    }

    @Test
    void resolvesGraderStackByName() {
        TurEvalGraderStack stack = new TurEvalGraderStack();
        stack.setId("stack-1");
        stack.setName("Strict RAG");
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(graderStackRepository.findById("Strict RAG")).thenReturn(Optional.empty());
        when(graderStackRepository.findFirstByName("Strict RAG")).thenReturn(Optional.of(stack));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), eq("stack-1")))
                .thenReturn(report(true, 0.8));

        TurEvalRunResultDto result =
                api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", "Strict RAG", null));

        assertThat(result.graderStackId()).isEqualTo("stack-1");
        assertThat(result.gatePassed()).isTrue();
    }

    @Test
    void minScore_failsGate_evenWhenEveryCasePassed() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(report(true, 0.7));

        TurEvalRunResultDto result = api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", null, 0.8));

        assertThat(result.report().passed()).isTrue();
        assertThat(result.gatePassed()).isFalse();
        assertThat(result.minScore()).isEqualTo(0.8);
    }

    @Test
    void minScore_passesGate_whenScoreMeetsThreshold() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(report(true, 0.85));

        TurEvalRunResultDto result = api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", null, 0.8));

        assertThat(result.gatePassed()).isTrue();
    }

    @Test
    void redRun_failsGate() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(report(false, 0.4));

        TurEvalRunResultDto result = api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", null, null));

        assertThat(result.gatePassed()).isFalse();
    }

    @Test
    void missingAgentId_is400() {
        assertThatThrownBy(() -> api.run(new TurEvalRunRequest(" ", "ds-1", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void missingDataset_is400() {
        assertThatThrownBy(() -> api.run(new TurEvalRunRequest(AGENT_ID, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dataset");
    }

    @Test
    void unknownDataset_is404() {
        when(datasetRepository.findById("nope")).thenReturn(Optional.empty());
        when(datasetRepository.findFirstByNameOrderByCreatedAtDesc("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.run(new TurEvalRunRequest(AGENT_ID, "nope", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Dataset not found");
    }

    @Test
    void unknownGraderStack_is404() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(graderStackRepository.findById("nope")).thenReturn(Optional.empty());
        when(graderStackRepository.findFirstByName("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", "nope", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Grader stack not found");
    }

    @Test
    void runnerError_mapsTo422() {
        when(datasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        lenient().when(runnerService.runDataset(eq(AGENT_ID), eq("ds-1"), isNull()))
                .thenReturn(TurAgentEvalReportDto.error("No usable LLM"));

        assertThatThrownBy(() -> api.run(new TurEvalRunRequest(AGENT_ID, "ds-1", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No usable LLM");
    }
}
