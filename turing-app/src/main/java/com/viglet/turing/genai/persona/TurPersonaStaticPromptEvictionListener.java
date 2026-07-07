/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.persona.TurPersona;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * JPA entity callback that wipes the {@link TurPersonaStaticPromptCache#CACHE_NAME
 * turPersonaStaticPrompt} cache whenever a {@link TurPersona} is persisted,
 * updated, or removed via JPA — so an operator editing the persona's
 * instruction / tone / vocabulary sees the new voice on the next chat turn
 * without an app restart.
 *
 * <p>Block AC / T486 removed the repository-level {@code @CacheEvict} that used
 * to invalidate this cache alongside the (now also removed) {@code turPersona*}
 * finder caches. Relocating the eviction to this JPA callback keeps it
 * write-path-agnostic (it fires on any {@code save(...)} caller, not just the
 * admin controller) — mirrors
 * {@link com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener} and
 * {@link com.viglet.turing.genai.flow.TurChatFlowRouterEvictionListener}.
 *
 * <p>The eviction is coarse ({@code clear()}): a single persona edit is rare
 * relative to chat turns, and the cache rebuilds lazily per persona id on the
 * next compose. The bulk-DML delete path
 * ({@code TurPersonaRepository.delete(id)}) bypasses JPA callbacks by design;
 * that one delete endpoint evicts explicitly.
 *
 * <p>Hibernate resolves this listener through Spring's {@code SpringBeanContainer},
 * so constructor injection of {@link CacheManager} works; the static fallback
 * covers the legacy path where JPA instantiates the listener with the no-arg
 * constructor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPersonaStaticPromptEvictionListener {

    private static final AtomicReference<CacheManager> cacheManagerStatic = new AtomicReference<>();

    private final CacheManager cacheManager;

    public TurPersonaStaticPromptEvictionListener() {
        this.cacheManager = null;
    }

    @Autowired
    public TurPersonaStaticPromptEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        cacheManagerStatic.set(cacheManager);
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(TurPersona entity) {
        CacheManager cm = cacheManager != null ? cacheManager : cacheManagerStatic.get();
        if (cm == null) {
            return;
        }
        Cache cache = cm.getCache(TurPersonaStaticPromptCache.CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }
}
