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

import com.viglet.turing.persistence.model.agent.TurRoutine;

/**
 * Repository for {@link TurRoutine} entities.
 *
 * <p>Caches follow the project convention (lowerCamelCase names paired with
 * eviction on every write path). The companion lint test
 * {@code TurCacheAnnotationConventionsTest} flags an unpaired cache name on
 * build.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurRoutineRepository extends JpaRepository<TurRoutine, String> {

    @Override
    @Cacheable("turRoutinefindAll")
    @NotNull
    List<TurRoutine> findAll();

    @Override
    @Cacheable("turRoutinefindById")
    @NotNull
    Optional<TurRoutine> findById(@NotNull String id);

    @Cacheable("turRoutinefindByName")
    Optional<TurRoutine> findByName(String name);

    @Override
    @CacheEvict(value = {"turRoutinefindAll", "turRoutinefindById", "turRoutinefindByName"},
            allEntries = true)
    @NotNull
    <S extends TurRoutine> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurRoutine r where r.id = ?1")
    @CacheEvict(value = {"turRoutinefindAll", "turRoutinefindById", "turRoutinefindByName"},
            allEntries = true)
    void delete(String id);
}
