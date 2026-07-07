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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Repository for {@link TurPersona}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurPersonaRepository extends JpaRepository<TurPersona, String> {

    /**
     * Case-insensitive lookup used by the chat-flow import pipeline to de-duplicate
     * personas embedded in an export file — the operator's catalog stores names
     * exactly as typed, so matching ignoring case prevents trivial casing
     * differences ("Captain Nova" vs "captain nova") from creating duplicates.
     *
     * @since 2026.2.7
     */
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

    @Modifying
    @Query("delete from TurPersona p where p.id = ?1")
    void delete(String id);
}
