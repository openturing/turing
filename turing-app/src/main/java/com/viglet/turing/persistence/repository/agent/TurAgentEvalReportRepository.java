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
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;

/**
 * T286 / §XV.2 — repository for eval-set run reports.
 *
 * <p>Intentionally <b>not</b> {@code @Cacheable}: reports are append-only
 * run history, queried newest-first right after a write, so caching would
 * only serve stale "latest" rows. (Exempt from the cache-pairing lint by
 * having no {@code @Cacheable} method.)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurAgentEvalReportRepository extends JpaRepository<TurAgentEvalReport, String> {

    List<TurAgentEvalReport> findByTurAIAgent_IdOrderByCreatedAtDesc(String agentId);

    Optional<TurAgentEvalReport> findFirstByTurAIAgent_IdOrderByCreatedAtDesc(String agentId);

    Optional<TurAgentEvalReport> findFirstByTurAIAgent_IdAndBaselineTrueOrderByCreatedAtDesc(String agentId);

    List<TurAgentEvalReport> findByTurAIAgent_IdAndBaselineTrue(String agentId);
}
