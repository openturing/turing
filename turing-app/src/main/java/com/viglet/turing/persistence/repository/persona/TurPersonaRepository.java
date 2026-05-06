/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.persona;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Repository for {@link TurPersona}. Mirrors the {@code TurAIAgentRepository}
 * caching convention — every read is cached, every write evicts every
 * persona cache.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurPersonaRepository extends JpaRepository<TurPersona, String> {
    @Override
    @Cacheable("turPersonafindAll")
    List<TurPersona> findAll();

    @Cacheable("turPersonafindAllSorted")
    List<TurPersona> findAll(@NotNull Sort sort);

    @Override
    @Cacheable("turPersonafindById")
    @NotNull
    Optional<TurPersona> findById(@NotNull String id);

    @CacheEvict(value = { "turPersonafindAll", "turPersonafindAllSorted",
            "turPersonafindById" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurPersona> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurPersona p where p.id = ?1")
    @CacheEvict(value = { "turPersonafindAll", "turPersonafindAllSorted",
            "turPersonafindById" }, allEntries = true)
    void delete(String id);
}
