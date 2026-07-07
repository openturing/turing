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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewTaskRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalDatasetRowRepository datasetRowRepository;

    public TurEvalReviewTaskService(TurEvalReviewTaskRepository reviewTaskRepository,
            TurAgentEvalReportRepository reportRepository,
            TurEvalDatasetRepository datasetRepository,
            TurEvalDatasetRowRepository datasetRowRepository) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.reportRepository = reportRepository;
        this.datasetRepository = datasetRepository;
        this.datasetRowRepository = datasetRowRepository;
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
     * §XXXIII.8 "closes the loop" — promotes a reviewed case into a golden
     * {@link TurEvalDatasetRow} appended to {@code datasetId}: the transcript's
     * user turns become the seed turns, the last assistant reply the golden
     * {@code referenceAnswer}, and the expected outcome/node are lifted from the
     * parked summary. Provenance (task/agent/grader/reviewer) is kept in
     * {@code metadataJson}. Bumps the dataset's {@code updatedAt}; returns the
     * refreshed dataset summary.
     */
    @Transactional
    public TurEvalDatasetDto promoteToDataset(String taskId, String datasetId) {
        TurEvalReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review task not found: " + taskId));
        TurEvalDataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dataset not found: " + datasetId));
        List<TurEvalDatasetRow> existing =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId);

        Map<String, String> expected = parseExpectedSummary(task.getExpectedSummary());
        TurEvalDatasetRow row = new TurEvalDatasetRow();
        row.setName(task.getCaseName());
        row.setSeedTurnsJson(userTurnsJson(task.getTranscript()));
        row.setReferenceAnswer(lastAssistantReply(task.getTranscript()));
        row.setExpectedOutcome(parseOutcome(expected.get("expectedOutcome")));
        row.setExpectedNodeId(nullIfBlankOrNull(expected.get("expectedNodeId")));
        row.setTags("promoted");
        row.setMetadataJson(promotionMetadata(task));
        row.setSortOrder(existing.size());
        row.setTurEvalDataset(dataset);
        datasetRowRepository.save(row);

        dataset.setUpdatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        datasetRepository.save(dataset);
        log.debug("[AgentEval] promoted review task '{}' into dataset '{}'", taskId, datasetId);
        return TurEvalDatasetDto.summary(dataset, existing.size() + 1);
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

    // ─────────────────────────── T593 promote-to-golden-row ───────────────────────────

    /** Parses the {@code "USER: ...\nASSISTANT: ..."} transcript into a JSON array of user turns. */
    private static String userTurnsJson(String transcript) {
        ArrayNode turns = OBJECT_MAPPER.createArrayNode();
        if (transcript != null) {
            for (String line : transcript.split("\\R")) {
                if (line.startsWith("USER: ")) {
                    turns.add(line.substring("USER: ".length()));
                }
            }
        }
        return turns.toString();
    }

    /** The last {@code ASSISTANT:} line of the transcript — the reply the reviewer signed off on. */
    private static String lastAssistantReply(String transcript) {
        if (transcript == null) {
            return null;
        }
        String reply = null;
        for (String line : transcript.split("\\R")) {
            if (line.startsWith("ASSISTANT: ")) {
                reply = line.substring("ASSISTANT: ".length());
            }
        }
        return reply;
    }

    /** Parses the {@code "k=v; k=v"} expected summary back into a map. */
    private static Map<String, String> parseExpectedSummary(String summary) {
        Map<String, String> out = new HashMap<>();
        if (summary == null || summary.isBlank()) {
            return out;
        }
        for (String pair : summary.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return out;
    }

    private static TurAgentEvalExpectedOutcome parseOutcome(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return TurAgentEvalExpectedOutcome.ANY;
        }
        try {
            return TurAgentEvalExpectedOutcome.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TurAgentEvalExpectedOutcome.ANY;
        }
    }

    private static String nullIfBlankOrNull(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }

    private static String promotionMetadata(TurEvalReviewTask task) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("source", "human-review");
        node.put("reviewTaskId", task.getId());
        node.put("agentId", task.getAgentId());
        node.put("graderId", task.getGraderId());
        node.put("reviewedBy", task.getReviewedBy());
        return node.toString();
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
