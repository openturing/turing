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

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchPersona;

/**
 * Repository for the project↔persona join {@link TurPersonaMatchPersona}
 * (Block AT / §XLIII).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaMatchPersonaRepository
        extends JpaRepository<TurPersonaMatchPersona, String> {

    List<TurPersonaMatchPersona> findByProject_Id(String projectId);

    @Modifying
    @Query("delete from TurPersonaMatchPersona j where j.project.id = ?1")
    void deleteByProjectId(String projectId);
}
