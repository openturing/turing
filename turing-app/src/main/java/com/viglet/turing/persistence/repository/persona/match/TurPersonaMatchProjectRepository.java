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

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSchedule;

/**
 * Repository for {@link TurPersonaMatchProject} (Block AT / §XLIII). Admin-CRUD
 * aggregate root — uncached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaMatchProjectRepository
        extends JpaRepository<TurPersonaMatchProject, String> {

    List<TurPersonaMatchProject> findByOrderByNameAsc();

    /** Enabled projects on a given cadence — the scheduled re-analysis feed. */
    List<TurPersonaMatchProject> findByEnabledTrueAndSchedule(TurPersonaMatchSchedule schedule);
}
