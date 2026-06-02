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

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurChatWebhook;

/**
 * Repository for {@link TurChatWebhook} entities (T62).
 *
 * <p>Caches follow the project convention (lowerCamelCase names paired with
 * eviction on every write path). The companion lint test
 * {@code TurCacheAnnotationConventionsTest} flags an unpaired cache name on
 * build. The dispatcher reads {@link #findAll()} (cached) on every slot
 * write, so the cache keeps the hot path off the database.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurChatWebhookRepository extends JpaRepository<TurChatWebhook, String> {

    @Override
    @Cacheable("turChatWebhookfindAll")
    @NotNull
    List<TurChatWebhook> findAll();

    @Override
    @Cacheable("turChatWebhookfindById")
    @NotNull
    Optional<TurChatWebhook> findById(@NotNull String id);

    @Cacheable("turChatWebhookfindByName")
    Optional<TurChatWebhook> findByName(String name);

    @Override
    @CacheEvict(value = {"turChatWebhookfindAll", "turChatWebhookfindById", "turChatWebhookfindByName"},
            allEntries = true)
    @NotNull
    <S extends TurChatWebhook> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurChatWebhook w where w.id = ?1")
    @CacheEvict(value = {"turChatWebhookfindAll", "turChatWebhookfindById", "turChatWebhookfindByName"},
            allEntries = true)
    void delete(String id);
}
