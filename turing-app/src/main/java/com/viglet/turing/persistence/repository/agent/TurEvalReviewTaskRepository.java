/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurEvalReviewTask;

/**
 * T592 / §XXXIII.7 — repository for human-review tasks parked by the
 * {@code human-review} grader.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurEvalReviewTaskRepository extends JpaRepository<TurEvalReviewTask, String> {

    List<TurEvalReviewTask> findByAgentIdAndStatusOrderByCreatedAtDesc(
            @Param("agentId") String agentId, @Param("status") String status);

    boolean existsByAgentIdAndCaseIdAndGraderIdAndStatus(
            @Param("agentId") String agentId, @Param("caseId") String caseId,
            @Param("graderId") String graderId, @Param("status") String status);

    long countByAgentIdAndStatus(@Param("agentId") String agentId, @Param("status") String status);

    /** T594 — all review tasks for an agent (any status), newest first. */
    List<TurEvalReviewTask> findByAgentIdOrderByCreatedAtDesc(@Param("agentId") String agentId);

    /** T594 — tasks of a given type in a given status (e.g. reviewed AUDIT tasks for calibration). */
    List<TurEvalReviewTask> findByAgentIdAndTaskTypeAndStatus(@Param("agentId") String agentId,
            @Param("taskType") String taskType, @Param("status") String status);
}
