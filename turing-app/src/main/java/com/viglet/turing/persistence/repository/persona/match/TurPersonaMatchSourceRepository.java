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

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSource;

/**
 * Repository for {@link TurPersonaMatchSource} (Block AT / §XLIII). The
 * project-scoped listing is the studio hot path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaMatchSourceRepository
        extends JpaRepository<TurPersonaMatchSource, String> {

    List<TurPersonaMatchSource> findByProject_IdOrderBySourceNameAsc(String projectId);

    long countByProject_Id(String projectId);
}
