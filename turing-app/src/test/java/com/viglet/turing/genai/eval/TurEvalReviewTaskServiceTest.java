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
import com.viglet.turing.persistence.dto.agent.TurEvalCalibrationDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;
import com.viglet.turing.persistence.model.agent.TurEvalReviewVerdict;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewTaskRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewVerdictRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * T592–T594 / §XXXIII.7-9 — the human-review task service: snapshotting a
 * deferred case, N-reviewer consensus, MODEL-grade audit sampling, and the
 * calibration compare.
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
    private TurEvalReviewVerdictRepository verdictRepository;
    @Mock
    private TurAgentEvalReportRepository reportRepository;
    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;

    private TurEvalReviewTaskService service() {
        return new TurEvalReviewTaskService(repository, verdictRepository, reportRepository,
                datasetRepository, datasetRowRepository);
    }

    private static TurAgentEvalCase evalCase() {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setId("case-1");
        c.setName("greeting");
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.CAPTURED);
        c.setExpectedNodeId("welcome");
        return c;
    }

    private static TurEvalReviewVerdict verdict(String reviewer, boolean pass, double score) {
        TurEvalReviewVerdict v = new TurEvalReviewVerdict();
        v.setReviewer(reviewer);
        v.setReviewedPass(pass);
        v.setReviewedScore(score);
        return v;
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
        assertThat(task.getTaskType()).isEqualTo(TurEvalReviewTask.TYPE_DEFERRED);
        assertThat(task.getRequiredReviewers()).isEqualTo(1);
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
        when(verdictRepository.existsByReviewTask_IdAndReviewer("t1", "alice")).thenReturn(false);
        when(verdictRepository.findByReviewTask_Id("t1"))
                .thenReturn(List.of(verdict("alice", true, 0.9)));

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
        verify(verdictRepository).save(any(TurEvalReviewVerdict.class));

        ArgumentCaptor<TurAgentEvalReport> captor = ArgumentCaptor.forClass(TurAgentEvalReport.class);
        verify(reportRepository).save(captor.capture());
        TurAgentEvalReport merged = captor.getValue();
        assertThat(merged.isPendingReview()).isFalse();
        assertThat(merged.isPassed()).isTrue();
        assertThat(merged.getPassedCount()).isEqualTo(1);
    }

    @Test
    void submitReviewBelowThresholdStaysPending() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setAgentId("a1");
        task.setStatus(TurEvalReviewTask.PENDING);
        task.setRequiredReviewers(3);
        when(repository.findById("t1")).thenReturn(Optional.of(task));
        when(verdictRepository.existsByReviewTask_IdAndReviewer("t1", "alice")).thenReturn(false);
        when(verdictRepository.findByReviewTask_Id("t1"))
                .thenReturn(List.of(verdict("alice", true, 1d)));

        TurEvalReviewTaskDto dto =
                service().submitReview("t1", new TurEvalReviewRequest(true, 1d, null), "alice");

        assertThat(task.getStatus()).isEqualTo(TurEvalReviewTask.PENDING);
        assertThat(dto.verdictCount()).isEqualTo(1);
        assertThat(dto.requiredReviewers()).isEqualTo(3);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void submitReviewReachingThresholdUsesStrictMajority() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setAgentId("a1");
        task.setCaseId("case-1");
        task.setStatus(TurEvalReviewTask.PENDING);
        task.setRequiredReviewers(3);
        when(repository.findById("t1")).thenReturn(Optional.of(task));
        when(verdictRepository.existsByReviewTask_IdAndReviewer("t1", "carol")).thenReturn(false);
        // 2 pass, 1 fail → strict majority passes.
        when(verdictRepository.findByReviewTask_Id("t1")).thenReturn(List.of(
                verdict("alice", true, 0.8), verdict("bob", false, 0.2), verdict("carol", true, 0.9)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc("a1"))
                .thenReturn(Optional.empty());

        service().submitReview("t1", new TurEvalReviewRequest(true, 0.9, null), "carol");

        assertThat(task.getStatus()).isEqualTo(TurEvalReviewTask.REVIEWED);
        assertThat(task.getReviewedPass()).isTrue();
        assertThat(task.getReviewedScore()).isCloseTo(0.633d, org.assertj.core.api.Assertions.within(0.01));
        assertThat(task.getReviewedBy()).contains("alice").contains("bob").contains("carol");
    }

    @Test
    void submitReviewRejectsDuplicateReviewer() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setStatus(TurEvalReviewTask.PENDING);
        when(repository.findById("t1")).thenReturn(Optional.of(task));
        when(verdictRepository.existsByReviewTask_IdAndReviewer("t1", "alice")).thenReturn(true);

        assertThatThrownBy(() -> service().submitReview("t1",
                new TurEvalReviewRequest(true, 1d, null), "alice"))
                .isInstanceOf(ResponseStatusException.class);
        verify(verdictRepository, never()).save(any());
    }

    @Test
    void promoteToDatasetAppendsGoldenRowFromTranscript() {
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setId("t1");
        task.setAgentId("a1");
        task.setCaseName("greeting");
        task.setGraderId("human-review");
        task.setReviewedBy("alice");
        task.setTranscript("USER: hi\nASSISTANT: hello\nUSER: bye\nASSISTANT: goodbye\n");
        task.setExpectedSummary(
                "expectedOutcome=CAPTURED; actualOutcome=CAPTURED; expectedNodeId=welcome; finalNodeId=end");
        when(repository.findById("t1")).thenReturn(Optional.of(task));

        TurEvalDataset dataset = new TurEvalDataset();
        dataset.setId("d1");
        dataset.setName("golden");
        when(datasetRepository.findById("d1")).thenReturn(Optional.of(dataset));
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("d1"))
                .thenReturn(List.of());

        var result = service().promoteToDataset("t1", "d1");

        ArgumentCaptor<TurEvalDatasetRow> captor = ArgumentCaptor.forClass(TurEvalDatasetRow.class);
        verify(datasetRowRepository).save(captor.capture());
        TurEvalDatasetRow row = captor.getValue();
        assertThat(row.getName()).isEqualTo("greeting");
        assertThat(row.getSeedTurnsJson()).contains("hi").contains("bye");
        assertThat(row.getReferenceAnswer()).isEqualTo("goodbye");
        assertThat(row.getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.CAPTURED);
        assertThat(row.getExpectedNodeId()).isEqualTo("welcome");
        assertThat(row.getTags()).isEqualTo("promoted");
        assertThat(row.getSortOrder()).isZero();
        assertThat(row.getTurEvalDataset()).isSameAs(dataset);
        verify(datasetRepository).save(dataset);
        assertThat(result.rowCount()).isEqualTo(1);
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

    @Test
    void sampleModelGradedAuditParksAuditTasksWithModelVerdict() {
        TurAgentEvalCaseResultDto graded = new TurAgentEvalCaseResultDto("case-1", "greeting",
                true, 0.8, "CAPTURED", "CAPTURED", "end", List.of(), "pass", "looks good", null, false);
        TurAgentEvalCaseResultDto naCase = new TurAgentEvalCaseResultDto("case-2", "no-rubric",
                true, 1d, "ANY", "CAPTURED", "end", List.of(), "na", null, null, false);
        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setResultsJson(MAPPER.writeValueAsString(List.of(graded, naCase)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc("a1"))
                .thenReturn(Optional.of(report));
        when(repository.existsByAgentIdAndCaseIdAndGraderIdAndStatus(
                "a1", "case-1", TurEvalReviewTask.AUDIT_GRADER, TurEvalReviewTask.PENDING))
                .thenReturn(false);

        List<TurEvalReviewTaskDto> parked = service().sampleModelGradedAudit("a1", 10, 2);

        assertThat(parked).hasSize(1);
        ArgumentCaptor<TurEvalReviewTask> captor = ArgumentCaptor.forClass(TurEvalReviewTask.class);
        verify(repository).save(captor.capture());
        TurEvalReviewTask task = captor.getValue();
        assertThat(task.getTaskType()).isEqualTo(TurEvalReviewTask.TYPE_AUDIT);
        assertThat(task.getGraderId()).isEqualTo(TurEvalReviewTask.AUDIT_GRADER);
        assertThat(task.getRequiredReviewers()).isEqualTo(2);
        assertThat(task.getModelPass()).isTrue();
        assertThat(task.getModelScore()).isEqualTo(0.8);
        assertThat(task.getTranscript()).contains("MODEL grader verdict").contains("looks good");
    }

    @Test
    void calibrationComparesModelToHumanConsensus() {
        TurEvalReviewTask agree = auditTask(true, true);
        TurEvalReviewTask overPass = auditTask(true, false);
        when(repository.findByAgentIdAndTaskTypeAndStatus(
                "a1", TurEvalReviewTask.TYPE_AUDIT, TurEvalReviewTask.REVIEWED))
                .thenReturn(List.of(agree, overPass));

        TurEvalCalibrationDto dto = service().calibration("a1");

        assertThat(dto.audited()).isEqualTo(2);
        assertThat(dto.modelPassHumanPass()).isEqualTo(1);
        assertThat(dto.modelPassHumanFail()).isEqualTo(1);
        assertThat(dto.agreementRate()).isEqualTo(0.5d);
        assertThat(dto.suggestion()).contains("over-passes");
    }

    @Test
    void calibrationEmptyWhenNoAuditTasks() {
        when(repository.findByAgentIdAndTaskTypeAndStatus(
                "a1", TurEvalReviewTask.TYPE_AUDIT, TurEvalReviewTask.REVIEWED))
                .thenReturn(List.of());

        assertThat(service().calibration("a1").audited()).isZero();
    }

    @Test
    void agreementBuildsFleissOverMultiReviewerTasks() {
        TurEvalReviewTask t1 = new TurEvalReviewTask();
        t1.setId("t1");
        TurEvalReviewTask t2 = new TurEvalReviewTask();
        t2.setId("t2");
        TurEvalReviewVerdict a = verdict("alice", true, 1d);
        a.setReviewTask(t1);
        TurEvalReviewVerdict b = verdict("bob", true, 1d);
        b.setReviewTask(t1);
        TurEvalReviewVerdict c = verdict("alice", false, 0d);
        c.setReviewTask(t2);
        TurEvalReviewVerdict d = verdict("bob", false, 0d);
        d.setReviewTask(t2);
        when(verdictRepository.findByReviewTask_AgentId("a1")).thenReturn(List.of(a, b, c, d));

        var dto = service().agreement("a1");

        assertThat(dto.items()).isEqualTo(2);
        assertThat(dto.kappa()).isEqualTo(1d);
        assertThat(dto.interpretation()).isEqualTo("almost-perfect");
    }

    private static TurEvalReviewTask auditTask(boolean modelPass, boolean humanPass) {
        TurEvalReviewTask t = new TurEvalReviewTask();
        t.setTaskType(TurEvalReviewTask.TYPE_AUDIT);
        t.setStatus(TurEvalReviewTask.REVIEWED);
        t.setModelPass(modelPass);
        t.setReviewedPass(humanPass);
        return t;
    }
}
