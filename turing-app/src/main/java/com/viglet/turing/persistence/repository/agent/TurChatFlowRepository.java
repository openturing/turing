/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurChatFlowRepository extends JpaRepository<TurChatFlow, String> {
    String FIND_ALL = "turChatFlowFindAll";
    String FIND_BY_ID = "turChatFlowFindById";
    String FIND_BY_AGENT_ID = "turChatFlowFindByAgentId";

    @Override
    @Cacheable(FIND_ALL)
    @NotNull
    List<TurChatFlow> findAll();

    @Override
    @Cacheable(FIND_BY_ID)
    @NotNull
    Optional<TurChatFlow> findById(@NotNull String id);

    @CacheEvict(value = { FIND_ALL, FIND_BY_ID, FIND_BY_AGENT_ID,
            "turChatFlowRouterDecision" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurChatFlow> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurChatFlow f where f.id = ?1")
    @CacheEvict(value = { FIND_ALL, FIND_BY_ID, FIND_BY_AGENT_ID,
            "turChatFlowRouterDecision" }, allEntries = true)
    void delete(String id);

    /**
     * Called once per chat turn from {@code TurAgentChatExecutor.resolveFlowContext}
     * to enumerate the flows attached to the agent. Cached because the flow
     * catalog changes rarely (admin edit) compared to chat turn frequency —
     * the {@code FIND_BY_AGENT_ID} cache is evicted on any
     * {@code save}/{@code delete} so admin edits propagate without restart.
     */
    @Cacheable(FIND_BY_AGENT_ID)
    List<TurChatFlow> findByTurAIAgent_IdOrderByNameAsc(String agentId);
}
