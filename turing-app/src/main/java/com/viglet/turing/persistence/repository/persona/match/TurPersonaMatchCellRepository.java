/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.persona.match;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCell;

/**
 * Repository for the N×N matrix cells {@link TurPersonaMatchCell}
 * (Block AT / §XLIII). Cells are written and read in bulk by the analysis
 * runner and the report aggregations.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaMatchCellRepository
        extends JpaRepository<TurPersonaMatchCell, String> {

    List<TurPersonaMatchCell> findByProjectId(String projectId);

    Optional<TurPersonaMatchCell> findByProjectIdAndSourceIdAndPersonaId(
            String projectId, String sourceId, String personaId);

    @Modifying
    @Query("delete from TurPersonaMatchCell c where c.projectId = ?1")
    void deleteByProjectId(String projectId);

    /** Prune cells for a content/persona removed from the project. */
    @Modifying
    @Query("delete from TurPersonaMatchCell c where c.projectId = ?1 and c.sourceId = ?2")
    void deleteByProjectIdAndSourceId(String projectId, String sourceId);

    @Modifying
    @Query("delete from TurPersonaMatchCell c where c.projectId = ?1 and c.personaId = ?2")
    void deleteByProjectIdAndPersonaId(String projectId, String personaId);
}
