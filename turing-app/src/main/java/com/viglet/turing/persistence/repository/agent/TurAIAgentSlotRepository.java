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

import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public interface TurAIAgentSlotRepository extends JpaRepository<TurAIAgentSlot, String> {
    String FIND_ALL = "turAIAgentSlotFindAll";
    String FIND_BY_ID = "turAIAgentSlotFindById";

    @Override
    @Cacheable(FIND_ALL)
    @NotNull
    List<TurAIAgentSlot> findAll();

    @Override
    @Cacheable(FIND_BY_ID)
    @NotNull
    Optional<TurAIAgentSlot> findById(@NotNull String id);

    @CacheEvict(value = { FIND_ALL, FIND_BY_ID }, allEntries = true)
    @NotNull
    @Override
    <S extends TurAIAgentSlot> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurAIAgentSlot s where s.id = ?1")
    @CacheEvict(value = { FIND_ALL, FIND_BY_ID }, allEntries = true)
    void delete(String id);

    List<TurAIAgentSlot> findByTurAIAgent_IdOrderByNameAsc(String agentId);

    Optional<TurAIAgentSlot> findByTurAIAgent_IdAndName(String agentId, String name);
}
