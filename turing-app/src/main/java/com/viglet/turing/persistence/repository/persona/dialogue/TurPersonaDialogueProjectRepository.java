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

import com.viglet.turing.persistence.model.persona.dialogue.TurPersonaDialogueProject;

/**
 * Repository for {@link TurPersonaDialogueProject} (Block AU / §XLIV).
 * Admin-CRUD aggregate root — uncached.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaDialogueProjectRepository
        extends JpaRepository<TurPersonaDialogueProject, String> {

    List<TurPersonaDialogueProject> findByOrderByNameAsc();
}
