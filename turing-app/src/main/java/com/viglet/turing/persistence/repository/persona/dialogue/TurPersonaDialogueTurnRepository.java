/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.persona.dialogue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.dialogue.TurPersonaDialogueTurn;

/**
 * Repository for the persisted last-run transcript {@link TurPersonaDialogueTurn}
 * (Block AU / §XLIV). Turns are written/read in bulk; a run replaces the whole
 * transcript.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaDialogueTurnRepository
        extends JpaRepository<TurPersonaDialogueTurn, String> {

    List<TurPersonaDialogueTurn> findByProjectIdOrderByTurnIndexAsc(String projectId);

    @Modifying
    @Query("delete from TurPersonaDialogueTurn t where t.projectId = ?1")
    void deleteByProjectId(String projectId);
}
