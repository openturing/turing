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
            "turChatFlowRouterDecision",
            // T31 / §IV.5 — flow edits invalidate the cached static
            // head/tail of the system-prompt addendum (node label,
            // aiInstruction, validationRule, next-step preview all may
            // have changed). Same eviction cadence as the router
            // decision cache.
            "turChatFlowStaticAddendumHead", "turChatFlowStaticAddendumTail" },
            allEntries = true)
    @NotNull
    @Override
    <S extends TurChatFlow> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurChatFlow f where f.id = ?1")
    @CacheEvict(value = { FIND_ALL, FIND_BY_ID, FIND_BY_AGENT_ID,
            "turChatFlowRouterDecision",
            // T31 / §IV.5 — flow edits invalidate the cached static
            // head/tail of the system-prompt addendum (node label,
            // aiInstruction, validationRule, next-step preview all may
            // have changed). Same eviction cadence as the router
            // decision cache.
            "turChatFlowStaticAddendumHead", "turChatFlowStaticAddendumTail" },
            allEntries = true)
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

    /**
     * T71 / §VII.8.b — enumerates every flow declared under one A/B
     * {@code experimentKey} (the variants of a single experiment). Used by the
     * champion-challenger promotion path (manual endpoint + daily job) to load
     * the arms it promotes / archives. Deliberately <b>not</b> {@code @Cacheable}:
     * it is invoked only on rare admin / scheduled promotion actions, and the
     * promotion immediately mutates the same rows via {@code save(...)} — caching
     * here would only add an eviction obligation for no read-throughput gain.
     */
    List<TurChatFlow> findByExperimentKey(String experimentKey);

    /**
     * T122 — owning agent id for a flow, fetched as a scalar so the
     * parked-conversations dashboard can resolve the agent title without
     * navigating the LAZY {@code turAIAgent} association on a {@code @Cacheable}
     * (potentially detached) {@code findAll} result. Deliberately not cached:
     * called only on the rare dashboard read.
     *
     * @since 2026.3.1
     */
    @Query("select f.turAIAgent.id from TurChatFlow f where f.id = :id")
    Optional<String> findAgentIdByFlowId(@org.springframework.data.repository.query.Param("id") String id);
}
