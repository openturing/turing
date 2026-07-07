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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.persona.TurPersonaSource;

/**
 * Repository for {@link TurPersonaSource}. The parent-scoped listing is the
 * admin hot path and changes on every extraction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaSourceRepository extends JpaRepository<TurPersonaSource, String> {

    @Modifying
    @Query("delete from TurPersonaSource s where s.id = ?1")
    void delete(String id);

    /** All sources of one persona, alphabetical. Uncached (admin hot path). */
    List<TurPersonaSource> findByTurPersona_IdOrderBySourceNameAsc(String personaId);

    long countByTurPersona_Id(String personaId);
}
