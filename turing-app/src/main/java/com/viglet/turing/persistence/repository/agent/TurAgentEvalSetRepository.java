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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;

/**
 * T285 / §XV.1 — repository for per-agent golden sets.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurAgentEvalSetRepository extends JpaRepository<TurAgentEvalSet, String> {

    @Modifying
    @Query("delete from TurAgentEvalSet s where s.id = ?1")
    void delete(String id);

    List<TurAgentEvalSet> findByTurAIAgent_IdOrderByNameAsc(String agentId);

    Optional<TurAgentEvalSet> findByTurAIAgent_IdAndName(String agentId, String name);
}
