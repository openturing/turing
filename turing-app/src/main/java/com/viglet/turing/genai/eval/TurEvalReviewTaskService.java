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

import com.viglet.turing.genai.eval.TurEvalAgreement.AgreementResult;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurEvalAgreementDto;
import com.viglet.turing.persistence.dto.agent.TurEvalCalibrationDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewRequest;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewTaskDto;
import com.viglet.turing.persistence.dto.agent.TurEvalReviewVerdictDto;
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
    private final TurEvalReviewVerdictRepository verdictRepository;
    private final TurAgentEvalReportRepository reportRepository;
    private final TurEvalDatasetRepository datasetRepository;
    private final TurEvalDatasetRowRepository datasetRowRepository;

    public TurEvalReviewTaskService(TurEvalReviewTaskRepository reviewTaskRepository,
            TurEvalReviewVerdictRepository verdictRepository,
            TurAgentEvalReportRepository reportRepository,
            TurEvalDatasetRepository datasetRepository,
            TurEvalDatasetRowRepository datasetRowRepository) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.verdictRepository = verdictRepository;
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

    /** Open (PENDING) review tasks for an agent as API views (with reviewer progress). */
    public List<TurEvalReviewTaskDto> openTaskViews(String agentId) {
        Map<String, Integer> counts = verdictCountsByTask(agentId);
        return openTasks(agentId).stream()
                .map(t -> TurEvalReviewTaskDto.fromEntity(t, counts.getOrDefault(t.getId(), 0)))
                .toList();
    }

    /** One review task by id (any status), with its individual verdicts attached. */
    public TurEvalReviewTaskDto getTask(String taskId) {
        TurEvalReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review task not found: " + taskId));
        List<TurEvalReviewVerdictDto> verdicts = verdictRepository.findByReviewTask_Id(taskId).stream()
                .map(TurEvalReviewVerdictDto::fromEntity).toList();
        return TurEvalReviewTaskDto.fromEntity(task, verdicts);
    }

    private Map<String, Integer> verdictCountsByTask(String agentId) {
        Map<String, Integer> counts = new HashMap<>();
        for (TurEvalReviewVerdict v : verdictRepository.findByReviewTask_AgentId(agentId)) {
            if (v.getReviewTask() != null) {
                counts.merge(v.getReviewTask().getId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Records a reviewer's verdict on a PENDING task. Each verdict is stored
     * individually (T594); a reviewer may vote only once per task. Once the
     * task's {@code requiredReviewers} verdicts are in, the <b>consensus</b>
     * (strict-majority pass, mean score) is computed, the task goes REVIEWED, and
     * the consensus <b>merges back</b> into the case's row in the agent's latest
     * report so an all-reviewed run can go green. Below the threshold the task
     * stays PENDING and returns its progress. A second submit on a REVIEWED task
     * — or a repeat vote by the same reviewer — 409s.
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
        if (reviewer != null
                && verdictRepository.existsByReviewTask_IdAndReviewer(taskId, reviewer)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already reviewed this task");
        }
        boolean pass = request.pass() != null && request.pass();
        double score = request.score() != null ? request.score() : (pass ? 1d : 0d);

        TurEvalReviewVerdict verdict = new TurEvalReviewVerdict();
        verdict.setReviewTask(task);
        verdict.setReviewer(reviewer);
        verdict.setReviewedPass(pass);
        verdict.setReviewedScore(score);
        verdict.setNotes(request.notes());
        verdict.setReviewedAt(LocalDateTime.now(ZoneId.systemDefault()));
        verdictRepository.save(verdict);

        List<TurEvalReviewVerdict> verdicts = verdictRepository.findByReviewTask_Id(taskId);
        int required = Math.max(1, task.getRequiredReviewers());
        if (verdicts.size() >= required) {
            finalizeConsensus(task, verdicts);
        }
        List<TurEvalReviewVerdictDto> views = verdicts.stream()
                .map(TurEvalReviewVerdictDto::fromEntity).toList();
        return TurEvalReviewTaskDto.fromEntity(task, views);
    }

    /** Computes the consensus over all verdicts, marks REVIEWED, and merges back. */
    private void finalizeConsensus(TurEvalReviewTask task, List<TurEvalReviewVerdict> verdicts) {
        int passVotes = (int) verdicts.stream().filter(TurEvalReviewVerdict::isReviewedPass).count();
        // Strict majority passes; a tie fails (the gate stays conservative).
        boolean consensusPass = passVotes * 2 > verdicts.size();
        double meanScore = verdicts.stream().mapToDouble(TurEvalReviewVerdict::getReviewedScore)
                .average().orElse(consensusPass ? 1d : 0d);
        String reviewers = verdicts.stream().map(TurEvalReviewVerdict::getReviewer)
                .filter(r -> r != null && !r.isBlank()).distinct()
                .reduce((a, b) -> a + ", " + b).orElse(null);
        String notes = verdicts.stream().map(TurEvalReviewVerdict::getNotes)
                .filter(n -> n != null && !n.isBlank()).distinct()
                .reduce((a, b) -> a + " | " + b).orElse(null);

        task.setReviewedPass(consensusPass);
        task.setReviewedScore(meanScore);
        task.setReviewerNotes(notes);
        task.setReviewedBy(reviewers);
        task.setReviewedAt(LocalDateTime.now(ZoneId.systemDefault()));
        task.setStatus(TurEvalReviewTask.REVIEWED);
        reviewTaskRepository.save(task);
        mergeIntoLatestReport(task, consensusPass, meanScore);
    }

    /**
     * Sets how many independent reviewers a still-open task needs before its
     * consensus is computed (T594). Enables N-reviewer agreement on a
     * HUMAN-grader-deferred task; AUDIT tasks set this at sampling time.
     */
    @Transactional
    public TurEvalReviewTaskDto setRequiredReviewers(String taskId, int count) {
        TurEvalReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Review task not found: " + taskId));
        if (TurEvalReviewTask.REVIEWED.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review task already decided");
        }
        task.setRequiredReviewers(Math.max(1, count));
        reviewTaskRepository.save(task);
        int have = verdictRepository.findByReviewTask_Id(taskId).size();
        return TurEvalReviewTaskDto.fromEntity(task, have);
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

    // ─────────────────── T594 audit sampling / agreement / calibration ───────────────────

    /**
     * §XXXIII.9 — samples up to {@code sampleSize} MODEL-graded cases from the
     * agent's latest report and parks each as an {@code AUDIT} review task so
     * humans can re-judge the LLM judge. Sampling is a deterministic even stride
     * across the eligible cases (stable across calls, no RNG). The model's
     * original verdict is stashed on the task for the later calibration compare.
     * Idempotent per open (agent, case) audit task.
     *
     * @param agentId          the agent to audit
     * @param sampleSize       max cases to sample ({@code <= 0} ⇒ all eligible)
     * @param requiredReviewers reviewers each parked audit task needs (min 1)
     * @return the freshly parked audit tasks (skips already-open ones)
     */
    @Transactional
    public List<TurEvalReviewTaskDto> sampleModelGradedAudit(String agentId, int sampleSize,
            int requiredReviewers) {
        TurAgentEvalReport report = reportRepository
                .findFirstByTurAIAgent_IdOrderByCreatedAtDesc(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No eval report to audit for agent: " + agentId));
        List<TurAgentEvalCaseResultDto> modelGraded =
                TurAgentEvalRunnerService.parseResults(report.getResultsJson()).stream()
                        .filter(TurEvalReviewTaskService::isModelGraded)
                        .toList();
        if (modelGraded.isEmpty()) {
            return List.of();
        }
        int required = Math.max(1, requiredReviewers);
        List<TurAgentEvalCaseResultDto> sampled = strideSample(modelGraded, sampleSize);
        List<TurEvalReviewTaskDto> parked = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        for (TurAgentEvalCaseResultDto r : sampled) {
            if (reviewTaskRepository.existsByAgentIdAndCaseIdAndGraderIdAndStatus(
                    agentId, r.caseId(), TurEvalReviewTask.AUDIT_GRADER, TurEvalReviewTask.PENDING)) {
                continue;
            }
            TurEvalReviewTask task = new TurEvalReviewTask();
            task.setAgentId(agentId);
            task.setCaseId(r.caseId());
            task.setCaseName(r.caseName());
            task.setGraderId(TurEvalReviewTask.AUDIT_GRADER);
            task.setTaskType(TurEvalReviewTask.TYPE_AUDIT);
            task.setRequiredReviewers(required);
            task.setModelPass(r.passed());
            task.setModelScore(r.score());
            task.setTranscript(buildAuditEvidence(r));
            task.setExpectedSummary("expectedOutcome=" + r.expectedOutcome()
                    + "; actualOutcome=" + r.actualOutcome()
                    + "; finalNodeId=" + r.finalNodeId());
            task.setStatus(TurEvalReviewTask.PENDING);
            task.setCreatedAt(now);
            reviewTaskRepository.save(task);
            parked.add(TurEvalReviewTaskDto.fromEntity(task, 0));
        }
        log.debug("[AgentEval] parked {} MODEL-grade audit task(s) for agent '{}'",
                parked.size(), agentId);
        return parked;
    }

    /**
     * §XXXIII.9 — inter-annotator agreement (Fleiss' kappa) over the agent's
     * review tasks that collected two or more reviewer verdicts.
     */
    public TurEvalAgreementDto agreement(String agentId) {
        Map<String, int[]> byTask = new HashMap<>();
        for (TurEvalReviewVerdict v : verdictRepository.findByReviewTask_AgentId(agentId)) {
            if (v.getReviewTask() == null) {
                continue;
            }
            int[] counts = byTask.computeIfAbsent(v.getReviewTask().getId(), k -> new int[2]);
            counts[v.isReviewedPass() ? 1 : 0]++;
        }
        List<int[]> items = byTask.values().stream().filter(c -> c[0] + c[1] >= 2).toList();
        AgreementResult r = TurEvalAgreement.fleiss(items, 2);
        return new TurEvalAgreementDto(r.items(), r.minRaters() == Integer.MAX_VALUE ? 0 : r.minRaters(),
                r.maxRaters(), r.percentAgreement(), r.kappa(), r.interpretation());
    }

    /**
     * §XXXIII.9 — MODEL-vs-human calibration over the reviewed AUDIT tasks: how
     * well the LLM judge agrees with the human consensus (Cohen's kappa) plus a
     * plain-language judge-prompt tuning hint (never auto-applied).
     */
    public TurEvalCalibrationDto calibration(String agentId) {
        List<TurEvalReviewTask> audited = reviewTaskRepository.findByAgentIdAndTaskTypeAndStatus(
                agentId, TurEvalReviewTask.TYPE_AUDIT, TurEvalReviewTask.REVIEWED).stream()
                .filter(t -> t.getModelPass() != null && t.getReviewedPass() != null)
                .toList();
        if (audited.isEmpty()) {
            return TurEvalCalibrationDto.empty();
        }
        // confusion[model][human]; index 1 = pass, 0 = fail.
        int[][] confusion = new int[2][2];
        for (TurEvalReviewTask t : audited) {
            int m = Boolean.TRUE.equals(t.getModelPass()) ? 1 : 0;
            int h = Boolean.TRUE.equals(t.getReviewedPass()) ? 1 : 0;
            confusion[m][h]++;
        }
        int mpHp = confusion[1][1];
        int mpHf = confusion[1][0];
        int mfHp = confusion[0][1];
        int mfHf = confusion[0][0];
        AgreementResult r = TurEvalAgreement.cohen(confusion);
        String suggestion = calibrationSuggestion(mpHf, mfHp);
        return new TurEvalCalibrationDto(audited.size(), r.percentAgreement(), r.kappa(),
                r.interpretation(), mpHp, mpHf, mfHp, mfHf, suggestion);
    }

    private static String calibrationSuggestion(int overPass, int overFail) {
        if (overPass == 0 && overFail == 0) {
            return "The judge fully agrees with human reviewers on the audited sample.";
        }
        if (overPass > overFail) {
            return "The judge over-passes: " + overPass + " case(s) it passed were failed by "
                    + "reviewers. Tighten the rubric's fail criteria or add negative examples to "
                    + "the judge prompt.";
        }
        if (overFail > overPass) {
            return "The judge over-fails: " + overFail + " case(s) it failed were passed by "
                    + "reviewers. Loosen or clarify the rubric's pass criteria in the judge prompt.";
        }
        return "Mixed disagreement (" + overPass + " over-pass, " + overFail + " over-fail): "
                + "review the judge rubric wording in both directions.";
    }

    /** A MODEL-graded, non-errored case with a stable id — eligible for a calibration audit. */
    private static boolean isModelGraded(TurAgentEvalCaseResultDto r) {
        return r.caseId() != null && r.error() == null && r.rubricVerdict() != null
                && !"na".equalsIgnoreCase(r.rubricVerdict());
    }

    /** Deterministic even-stride sample of up to {@code n} items (n<=0 ⇒ all). */
    private static <T> List<T> strideSample(List<T> items, int n) {
        int size = items.size();
        if (n <= 0 || n >= size) {
            return items;
        }
        List<T> out = new ArrayList<>(n);
        // Spread picks across the list: index round(i * size / n).
        for (int i = 0; i < n; i++) {
            int idx = (int) ((long) i * size / n);
            out.add(items.get(Math.min(idx, size - 1)));
        }
        return out;
    }

    /** Renders the model grader's recorded verdict + rationale for a human auditor. */
    private static String buildAuditEvidence(TurAgentEvalCaseResultDto r) {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL grader verdict under audit\n");
        sb.append("  rubric: ").append(r.rubricVerdict())
                .append("  (pass=").append(r.passed())
                .append(", score=").append(String.format(java.util.Locale.ROOT, "%.2f", r.score()))
                .append(")\n");
        sb.append("  outcome: expected ").append(r.expectedOutcome())
                .append(" / actual ").append(r.actualOutcome()).append('\n');
        if (r.finalNodeId() != null) {
            sb.append("  final node: ").append(r.finalNodeId()).append('\n');
        }
        if (r.rubricRationale() != null && !r.rubricRationale().isBlank()) {
            sb.append("\nJudge rationale:\n").append(r.rubricRationale()).append('\n');
        }
        if (r.slotDiffs() != null && !r.slotDiffs().isEmpty()) {
            sb.append("\nSlots:\n");
            for (TurAgentEvalCaseResultDto.SlotDiff d : r.slotDiffs()) {
                sb.append("  ").append(d.slot()).append(": expected ").append(d.expected())
                        .append(" / actual ").append(d.actual())
                        .append(d.match() ? " (match)" : " (differs)").append('\n');
            }
        }
        sb.append("\nDecide whether the model's pass/fail verdict was correct.");
        return sb.toString();
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
