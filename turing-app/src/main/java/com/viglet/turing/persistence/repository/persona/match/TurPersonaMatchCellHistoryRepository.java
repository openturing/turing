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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchCellHistory;

/**
 * Repository for the append-only per-cell fit series
 * {@link TurPersonaMatchCellHistory} (Block AT / §XLIII, T704). Read in bulk per
 * project (trend chart, drift report) and pruned alongside the cells whenever a
 * content, a persona or the whole project goes away.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaMatchCellHistoryRepository
        extends JpaRepository<TurPersonaMatchCellHistory, String> {

    /** The whole project series, oldest first (the trend/drift read path). */
    List<TurPersonaMatchCellHistory> findByProjectIdOrderByEvaluatedAtAsc(String projectId);

    /** One cell's series, oldest first (the drill-in sparkline + retention trim). */
    List<TurPersonaMatchCellHistory> findByProjectIdAndSourceIdAndPersonaIdOrderByEvaluatedAtAsc(
            String projectId, String sourceId, String personaId);

    @Modifying
    @Query("delete from TurPersonaMatchCellHistory h where h.projectId = ?1")
    void deleteByProjectId(String projectId);

    @Modifying
    @Query("delete from TurPersonaMatchCellHistory h where h.projectId = ?1 and h.sourceId = ?2")
    void deleteByProjectIdAndSourceId(String projectId, String sourceId);

    @Modifying
    @Query("delete from TurPersonaMatchCellHistory h where h.projectId = ?1 and h.personaId = ?2")
    void deleteByProjectIdAndPersonaId(String projectId, String personaId);

    /** Prune exactly one pair's series (the runner's orphan-cell safety net). */
    @Modifying
    @Query("delete from TurPersonaMatchCellHistory h where h.projectId = ?1 "
            + "and h.sourceId = ?2 and h.personaId = ?3")
    void deleteByProjectIdAndSourceIdAndPersonaId(String projectId, String sourceId,
            String personaId);
}
