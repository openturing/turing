/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.distillation;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.genai.distillation.TurDistillationState;
import com.viglet.turing.persistence.model.distillation.TurDistillationJob;

/**
 * F.9 / §X.10.c — tracked distillation jobs.
 *
 * <p>No {@code @Cacheable}: the only hot reader is the poller (which wants fresh
 * rows) and the operator surface is cold — mirrors {@code TurBatchJobRepository}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurDistillationJobRepository extends JpaRepository<TurDistillationJob, String> {

    /** Non-terminal jobs the poller must still drive to completion, oldest first. */
    List<TurDistillationJob> findByStateOrderByCreatedAtAsc(TurDistillationState state);

    /** Recent jobs for an agent, newest first (operator surface). */
    List<TurDistillationJob> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);
}
