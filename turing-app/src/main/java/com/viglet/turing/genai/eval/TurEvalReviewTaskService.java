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
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;
import com.viglet.turing.persistence.repository.agent.TurEvalReviewTaskRepository;

import lombok.extern.slf4j.Slf4j;

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

    private final TurEvalReviewTaskRepository reviewTaskRepository;

    public TurEvalReviewTaskService(TurEvalReviewTaskRepository reviewTaskRepository) {
        this.reviewTaskRepository = reviewTaskRepository;
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
