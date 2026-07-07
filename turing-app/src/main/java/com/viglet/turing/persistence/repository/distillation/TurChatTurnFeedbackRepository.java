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

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.distillation.TurChatTurnFeedback;

/**
 * F.9 / §X.10.d — operator preference feedback rows.
 *
 * <p>No {@code @Cacheable}: written on every thumb-up/down and read only when
 * exporting the (cold) DPO dataset — mirrors {@code TurDistillationJobRepository}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurChatTurnFeedbackRepository extends JpaRepository<TurChatTurnFeedback, String> {

    /** All feedback for an agent, oldest first (deterministic DPO pairing order). */
    List<TurChatTurnFeedback> findByAgentIdOrderByCreatedAtAsc(String agentId);

    long countByAgentId(String agentId);
}
