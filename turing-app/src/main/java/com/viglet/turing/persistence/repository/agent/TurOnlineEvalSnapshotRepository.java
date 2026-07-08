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

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurOnlineEvalSnapshot;

/**
 * T603 / §XXXIII.18 — persisted online-eval snapshots.
 *
 * <p>No {@code @Cacheable}: rows are appended by the background sweep and read
 * by the operator drift panel (neither hot), so an eviction surface would be
 * pure cost. Mirrors {@link TurChatCitationRecordRepository}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurOnlineEvalSnapshotRepository extends JpaRepository<TurOnlineEvalSnapshot, String> {

    /** Newest snapshots first — powers the Studio timeline / run list. */
    List<TurOnlineEvalSnapshot> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    /** The single most recent snapshot for an agent. */
    Optional<TurOnlineEvalSnapshot> findFirstByAgentIdOrderByCreatedAtDesc(String agentId);

    /** The agent's current healthy baseline (the drift yardstick), newest wins. */
    Optional<TurOnlineEvalSnapshot> findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(String agentId);

    /** Every snapshot currently flagged baseline for an agent (demote-all helper). */
    List<TurOnlineEvalSnapshot> findByAgentIdAndBaselineTrue(String agentId);
}
