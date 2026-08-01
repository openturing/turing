/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.answer;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.answer.TurAnswerSignalEvent;

/**
 * T845 / §LXII.14 — persistence for {@link TurAnswerSignalEvent answer-level
 * signals}. The self-tuning miner reads recent relevance-misses for an agent to
 * fold answer feedback into its failure digest.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurAnswerSignalRepository extends JpaRepository<TurAnswerSignalEvent, String> {

    /**
     * The agent's recent relevance-miss signals (facet-refined-after-answer,
     * thumbs-down), newest first — the miner's input for the answer half of the
     * failure digest.
     */
    List<TurAnswerSignalEvent> findByAgentIdAndRelevanceMissTrueAndCreatedAtAfterOrderByCreatedAtDesc(
            String agentId, Instant after);
}
