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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewTaskRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * T592 / §XXXIII.7 — parks and queries human-review tasks. The {@code
 * human-review} grader defers a case; this service snapshots the replayed
 * transcript into a {@link TurEvalReviewTask} the reviewer later decides
 * (T593 inbox). Idempotent per (agent, case, grader) while a task is still
 * {@code PENDING}, so re-running the gate doesn't pile up duplicates.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEvalReviewTaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurEvalReviewTaskRepository reviewTaskRepository;
    private final TurAgentEvalReportRepository reportRepository;

    public TurEvalReviewTaskService(TurEvalReviewTaskRepository reviewTaskRepository,
            TurAgentEvalReportRepository reportRepository) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reportRepository = reportRepository;
    }

    /** Creates a PENDING review task for a deferred case (skips an existing open one). */
    @Transactional
    public void createIfAbsent(String agentId, TurAgentEvalCase evalCase, String graderId,
            List<String> userTurns, List<String> assistantReplies, String finalNodeId,
            String actualOutcome) {
        String caseId = evalCase.getId();
        if (reviewTaskRepository.existsByAgentIdAndCaseIdAndGraderIdAndStatus(
                agentId, caseId, graderId, TurEvalReviewTask.PENDING)) {
            return;
        }
        TurEvalReviewTask task = new TurEvalReviewTask();
        task.setAgentId(agentId);
        task.setCaseId(caseId);
        task.setCaseName(evalCase.getName());
        task.setGraderId(graderId);
        task.setTranscript(buildTranscript(userTurns, assistantReplies));
        task.setExpectedSummary(buildExpectedSummary(evalCase, finalNodeId, actualOutcome));
        task.setStatus(TurEvalReviewTask.PENDING);
        task.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        reviewTaskRepository.save(task);
        log.debug("[AgentEval] parked human-review task for case '{}' (grader {})",
                evalCase.getName(), graderId);
    }

    /** Open (PENDING) review tasks for an agent, newest first. */
    public List<TurEvalReviewTask> openTasks(String agentId) {
        return reviewTaskRepository.findByAgentIdAndStatusOrderByCreatedAtDesc(
                agentId, TurEvalReviewTask.PENDING);
    }

    /** Count of open (PENDING) review tasks for an agent. */
    public long openCount(String agentId) {
        return reviewTaskRepository.countByAgentIdAndStatus(agentId, TurEvalReviewTask.PENDING);
    }

    // ─────────────────────────── T593 review inbox ───────────────────────────

    /** Open (PENDING) review tasks for an agent as API views. */
    public List<TurEvalReviewTaskDto> openTaskViews(String agentId) {
        return openTasks(agentId).stream().map(TurEvalReviewTaskDto::fromEntity).toList();
    }

    /** One review task by id (any status). */
    public TurEvalReviewTaskDto getTask(String taskId) {
        return reviewTaskRepository.findById(taskId)
                .map(TurEvalReviewTaskDto::fromEntity)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review task not found: " + taskId));
    }

    /**
     * Records a reviewer's verdict on a PENDING task (idempotent — a second
     * submit on a REVIEWED task 409s) and <b>merges it back</b> into the case's
     * row in the agent's latest report, recomputing the report's pending/passed
     * state so an all-reviewed run can go green.
     */
    @Transactional
    public TurEvalReviewTaskDto submitReview(String taskId, TurEvalReviewRequest request,
            String reviewer) {
        TurEvalReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review task not found: " + taskId));
        if (TurEvalReviewTask.REVIEWED.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review task already decided");
        }
        boolean pass = request.pass() != null && request.pass();
        double score = request.score() != null ? request.score() : (pass ? 1d : 0d);
        task.setReviewedPass(pass);
        task.setReviewedScore(score);
        task.setReviewerNotes(request.notes());
        task.setReviewedBy(reviewer);
        task.setReviewedAt(LocalDateTime.now(ZoneId.systemDefault()));
        task.setStatus(TurEvalReviewTask.REVIEWED);
        reviewTaskRepository.save(task);
        mergeIntoLatestReport(task, pass, score);
        return TurEvalReviewTaskDto.fromEntity(task);
    }

    /**
     * Applies the human verdict to the matching case row of the agent's latest
     * report and recomputes its aggregate. No-op when the report or case can't
     * be found (e.g. a newer run replaced it).
     */
    private void mergeIntoLatestReport(TurEvalReviewTask task, boolean pass, double score) {
        Optional<TurAgentEvalReport> latest =
                reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(task.getAgentId());
        if (latest.isEmpty()) {
            return;
        }
        TurAgentEvalReport report = latest.get();
        List<TurAgentEvalCaseResultDto> results =
                TurAgentEvalRunnerService.parseResults(report.getResultsJson());
        List<TurAgentEvalCaseResultDto> merged = new ArrayList<>(results.size());
        boolean changed = false;
        for (TurAgentEvalCaseResultDto r : results) {
            if (r.pendingReview() && r.caseId() != null && r.caseId().equals(task.getCaseId())) {
                merged.add(new TurAgentEvalCaseResultDto(r.caseId(), r.caseName(), pass, score,
                        r.expectedOutcome(), r.actualOutcome(), r.finalNodeId(), r.slotDiffs(),
                        r.rubricVerdict(), r.rubricRationale(), r.error(), false));
                changed = true;
            } else {
                merged.add(r);
            }
        }
        if (!changed) {
            return;
        }
        boolean stillPending = merged.stream().anyMatch(TurAgentEvalCaseResultDto::pendingReview);
        int passedCount = (int) merged.stream().filter(TurAgentEvalCaseResultDto::passed).count();
        report.setPassedCount(passedCount);
        report.setPendingReview(stillPending);
        report.setPassed(!merged.isEmpty() && passedCount == merged.size() && !stillPending);
        report.setResultsJson(writeJson(merged));
        reportRepository.save(report);
    }

    private static String writeJson(List<TurAgentEvalCaseResultDto> results) {
        try {
            return OBJECT_MAPPER.writeValueAsString(results);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] could not serialize merged results: {}", e.getMessage());
            return "[]";
        }
    }

    private static String buildTranscript(List<String> userTurns, List<String> assistantReplies) {
        StringBuilder sb = new StringBuilder();
        int turns = userTurns == null ? 0 : userTurns.size();
        for (int i = 0; i < turns; i++) {
            sb.append("USER: ").append(userTurns.get(i)).append('\n');
            if (assistantReplies != null && i < assistantReplies.size()) {
                sb.append("ASSISTANT: ").append(assistantReplies.get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildExpectedSummary(TurAgentEvalCase evalCase, String finalNodeId,
            String actualOutcome) {
        return "expectedOutcome=" + evalCase.getExpectedOutcome()
                + "; actualOutcome=" + actualOutcome
                + "; expectedNodeId=" + evalCase.getExpectedNodeId()
                + "; finalNodeId=" + finalNodeId;
    }
}
