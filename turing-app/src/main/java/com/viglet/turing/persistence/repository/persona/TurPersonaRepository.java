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
import org.springframework.cache.annotation.Caching;

/**
 * Repository for {@link TurPersona}. Mirrors the {@code TurAIAgentRepository}
 * caching convention — every read is cached, every write evicts every
 * persona cache.
 *
 * <p>Writes also nuke the agent caches ({@code turAIAgentfindAll} and
 * {@code turAIAgentfindById}) because cached agent entities EAGER-fetch
 * their persona references — without this, an updated persona's
 * {@code systemInstruction} would still be served from the agent's
 * cached entity tree, and chats would keep speaking in the old voice.
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

    /**
     * Case-insensitive lookup used by the chat-flow import pipeline to de-duplicate
     * personas embedded in an export file — the operator's catalog stores names
     * exactly as typed, so matching ignoring case prevents trivial casing
     * differences ("Captain Nova" vs "captain nova") from creating duplicates.
     *
     * @since 2026.2.7
     */
    @Cacheable("turPersonafindByNameIgnoreCase")
    Optional<TurPersona> findByNameIgnoreCase(String name);

    /**
     * Used by the MCP-server delete path to find personas with their
     * {@code brandContextMcpServer} pointing at the server. The caller
     * nulls the reference + saveAndFlush before the server is deleted.
     *
     * @since 2026.2.8
     */
    List<TurPersona> findByBrandContextMcpServer_Id(String mcpServerId);

    /** Used by the store delete path (persona few-shot store). @since 2026.2.8 */
    List<TurPersona> findByFewShotStore_Id(String storeId);

    @Caching(evict = {
            @CacheEvict(value = { "turPersonafindAll", "turPersonafindAllSorted",
                    "turPersonafindById", "turPersonafindByNameIgnoreCase" }, allEntries = true),
            @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" },
                    allEntries = true),
            // T31 / §IV.5 — persona edits change the composed static
            // prompt block; the cache must drop alongside the persona
            // lookups so the next chat turn sees the new voice.
            @CacheEvict(value = "turPersonaStaticPrompt", allEntries = true)
    })
    @NotNull
    @Override
    <S extends TurPersona> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurPersona p where p.id = ?1")
    @Caching(evict = {
            @CacheEvict(value = { "turPersonafindAll", "turPersonafindAllSorted",
                    "turPersonafindById", "turPersonafindByNameIgnoreCase" }, allEntries = true),
            @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" },
                    allEntries = true),
            // T31 / §IV.5 — persona edits change the composed static
            // prompt block; the cache must drop alongside the persona
            // lookups so the next chat turn sees the new voice.
            @CacheEvict(value = "turPersonaStaticPrompt", allEntries = true)
    })
    void delete(String id);
}
