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

import com.viglet.turing.persistence.model.agent.TurEvalReviewVerdict;

/**
 * T594 / §XXXIII.9 — repository for individual reviewer verdicts backing the
 * N-reviewer / inter-annotator-agreement layer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurEvalReviewVerdictRepository extends JpaRepository<TurEvalReviewVerdict, String> {

    List<TurEvalReviewVerdict> findByReviewTask_Id(@Param("taskId") String taskId);

    boolean existsByReviewTask_IdAndReviewer(@Param("taskId") String taskId,
            @Param("reviewer") String reviewer);

    List<TurEvalReviewVerdict> findByReviewTask_AgentId(@Param("agentId") String agentId);
}
