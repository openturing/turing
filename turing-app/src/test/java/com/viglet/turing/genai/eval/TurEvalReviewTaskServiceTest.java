/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewTaskRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * T592 / §XXXIII.7 — the human-review task service: snapshotting a deferred case
 * and idempotency while a task is still PENDING.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalReviewTaskServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TurEvalReviewTaskRepository repository;
    @Mock
    private TurAgentEvalReportRepository reportRepository;

    private TurEvalReviewTaskService service() {
        return new TurEvalReviewTaskService(repository, reportRepository);
    }

    private static TurAgentEvalCase evalCase() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setId("case-1");
        c.setName("greeting");
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.CAPTURED);
        c.setExpectedNodeId("welcome");
        return c;
    }

    @Test
    void createsPendingTaskWithTranscriptSnapshot() {
        when(repository.existsByAgentIdAndCaseIdAndGraderIdAndStatus(
                eq("a1"), eq("case-1"), eq("human-review"), eq(TurEvalReviewTask.PENDING)))
                .thenReturn(false);

        service().createIfAbsent("a1", evalCase(), "human-review",
                List.of("hi", "bye"), List.of("hello", "goodbye"), "end", "CAPTURED");

        ArgumentCaptor<TurEvalReviewTask> captor = ArgumentCaptor.forClass(TurEvalReviewTask.class);
        verify(repository).save(captor.capture());
        TurEvalReviewTask task = captor.getValue();
        assertThat(task.getAgentId()).isEqualTo("a1");
        assertThat(task.getCaseId()).isEqualTo("case-1");
        assertThat(task.getStatus()).isEqualTo(TurEvalReviewTask.PENDING);
        assertThat(task.getTranscript()).contains("USER: hi").contains("ASSISTANT: hello")
                .contains("USER: bye");
        assertThat(task.getExpectedSummary()).contains("expectedOutcome=CAPTURED")
                .contains("finalNodeId=end");
        assertThat(task.getCreatedAt()).isNotNull();
    }

    @Test
    void skipsWhenOpenTaskAlreadyExists() {
        when(repository.existsByAgentIdAndCaseIdAndGraderIdAndStatus(
                any(), any(), any(), any())).thenReturn(true);

        service().createIfAbsent("a1", evalCase(), "human-review", List.of("hi"), List.of("hello"),
                "end", "CAPTURED");

        verify(repository, never()).save(any());
    }

    @Test
    void submitReviewMergesVerdictIntoLatestReport() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setAgentId("a1");
        task.setCaseId("case-1");
        task.setStatus(TurEvalReviewTask.PENDING);
        when(repository.findById("t1")).thenReturn(Optional.of(task));

        TurAgentEvalCaseResultDto pending = new TurAgentEvalCaseResultDto("case-1", "greeting",
                false, 0d, "CAPTURED", "ABANDONED", null, List.of(), "na", null, null, true);
        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setId("r1");
        report.setPendingReview(true);
        report.setResultsJson(MAPPER.writeValueAsString(List.of(pending)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc("a1"))
                .thenReturn(Optional.of(report));

        service().submitReview("t1", new TurEvalReviewRequest(true, 0.9, "looks fine"), "alice");

        assertThat(task.getStatus()).isEqualTo(TurEvalReviewTask.REVIEWED);
        assertThat(task.getReviewedPass()).isTrue();
        assertThat(task.getReviewedBy()).isEqualTo("alice");

        ArgumentCaptor<TurAgentEvalReport> captor = ArgumentCaptor.forClass(TurAgentEvalReport.class);
        verify(reportRepository).save(captor.capture());
        TurAgentEvalReport merged = captor.getValue();
        assertThat(merged.isPendingReview()).isFalse();
        assertThat(merged.isPassed()).isTrue();
        assertThat(merged.getPassedCount()).isEqualTo(1);
    }

    @Test
    void submitReviewOnAlreadyReviewedTaskConflicts() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setStatus(TurEvalReviewTask.REVIEWED);
        when(repository.findById("t1")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service().submitReview("t1",
                new TurEvalReviewRequest(true, 1d, null), "bob"))
                .isInstanceOf(ResponseStatusException.class);
        verify(reportRepository, never()).save(any());
    }
}
