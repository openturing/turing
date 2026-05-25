/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * T27 / §II.2.3 — JPA entity callback that wipes the
 * {@link TurChatFlowEngineService#evictRouterIndexes() per-agent router
 * index cache} whenever any {@link TurChatFlow} is persisted, updated, or
 * removed via JPA. Mirrors the existing {@code @CacheEvict} chain on
 * {@link com.viglet.turing.persistence.repository.agent.TurChatFlowRepository}
 * that already wipes {@code turChatFlowRouterDecision} on the same events;
 * the router index cache, being non-serializable Lucene state, can't ride
 * the same Spring cache and gets its own JPA-driven hook.
 *
 * <p>Hibernate (via Spring Boot's {@code SpringBeanContainer}) resolves
 * this listener through the application context, so constructor injection
 * of {@link TurChatFlowEngineService} works. The static fallback covers
 * the legacy path where JPA instantiates the listener with the no-arg
 * constructor — mirrors
 * {@link com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener}.
 *
 * <p>Bulk-DML deletes on the repository
 * ({@code @Query("delete from TurChatFlow f where f.id = ?1")}) bypass JPA
 * entity callbacks by design; the one API endpoint that uses that path
 * calls {@link TurChatFlowEngineService#evictRouterIndexes()} explicitly
 * so eviction stays consistent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurChatFlowRouterEvictionListener {

    private static volatile TurChatFlowEngineService engineStatic;

    private final TurChatFlowEngineService engine;

    public TurChatFlowRouterEvictionListener() {
        this.engine = null;
    }

    @Autowired
    public TurChatFlowRouterEvictionListener(@Lazy TurChatFlowEngineService engine) {
        this.engine = engine;
        TurChatFlowRouterEvictionListener.engineStatic = engine;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(TurChatFlow entity) {
        TurChatFlowEngineService target = engine != null ? engine : engineStatic;
        if (target != null) {
            target.evictRouterIndexes();
        }
    }
}
