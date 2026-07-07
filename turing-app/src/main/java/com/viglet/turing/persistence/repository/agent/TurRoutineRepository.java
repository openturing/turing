/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurRoutine;

/**
 * Repository for {@link TurRoutine} entities.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurRoutineRepository extends JpaRepository<TurRoutine, String> {

    Optional<TurRoutine> findByName(String name);

    @Modifying
    @Query("delete from TurRoutine r where r.id = ?1")
    void delete(String id);
}
