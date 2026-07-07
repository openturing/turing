/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.customtool;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.customtool.TurCustomTool;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurCustomToolRepository extends JpaRepository<TurCustomTool, String> {

    /** Agent-import fallback by title. @since 2026.2.8 */
    Optional<TurCustomTool> findByTitleIgnoreCase(String title);

    @Modifying
    @Query("delete from TurCustomTool ct where ct.id = ?1")
    void delete(String id);
}
