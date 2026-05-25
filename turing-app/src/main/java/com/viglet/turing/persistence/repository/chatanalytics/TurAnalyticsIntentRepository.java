/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.chatanalytics;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;

/**
 * T28 / §III.5 — JPA repository for {@link TurAnalyticsIntent}.
 *
 * <p>The classifier hot path is
 * {@link #findByTurAIAgent_IdAndEnabledOrderByLabelAsc} (called once per
 * enricher cycle, per agent); cached because the catalog rarely changes
 * compared to enrich cadence. {@code save}/{@code delete} wipe the cache
 * <em>and</em> the {@code turAnalyticsIntentMLTIndex} entry rebuilt by
 * {@code TurLuceneIntentClassifier} — admin edits propagate without
 * restart, same hook pattern T27 uses for
 * {@code turChatFlowRouterDecision}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurAnalyticsIntentRepository extends JpaRepository<TurAnalyticsIntent, String> {

    String FIND_ALL = "turAnalyticsIntentFindAll";
    String FIND_BY_AGENT_ID = "turAnalyticsIntentFindByAgentId";
    String LUCENE_INDEX = "turAnalyticsIntentMLTIndex";

    @Override
    @Cacheable(FIND_ALL)
    @NotNull
    List<TurAnalyticsIntent> findAll();

    @CacheEvict(value = { FIND_ALL, FIND_BY_AGENT_ID, LUCENE_INDEX }, allEntries = true)
    @Override
    @NotNull
    <S extends TurAnalyticsIntent> S save(@NotNull S entity);

    @CacheEvict(value = { FIND_ALL, FIND_BY_AGENT_ID, LUCENE_INDEX }, allEntries = true)
    @Override
    void delete(@NotNull TurAnalyticsIntent entity);

    @CacheEvict(value = { FIND_ALL, FIND_BY_AGENT_ID, LUCENE_INDEX }, allEntries = true)
    @Override
    void deleteById(@NotNull String id);

    /**
     * Hot path called once per enricher cycle per agent. Cached because
     * the catalog mutates on admin action (rare) while the cycle runs on
     * a cron (frequent).
     */
    @Cacheable(FIND_BY_AGENT_ID)
    List<TurAnalyticsIntent> findByTurAIAgent_IdAndEnabledOrderByLabelAsc(String agentId,
            int enabled);

    /**
     * Admin-listing variant — returns enabled + disabled rows. Not
     * cached; this powers the admin UI which is low-traffic compared to
     * the classifier hot path.
     */
    List<TurAnalyticsIntent> findByTurAIAgent_IdOrderByLabelAsc(String agentId);
}
