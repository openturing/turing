/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.research;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;

/**
 * Spring Data repository for the study↔persona audience roster join
 * (Block AW / §XLVI.2, T719).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurResearchStudyPersonaRepository
        extends JpaRepository<TurResearchStudyPersona, String> {

    List<TurResearchStudyPersona> findByStudy_IdOrderByPositionAsc(String studyId);

    @Modifying
    @Query("delete from TurResearchStudyPersona j where j.study.id = ?1")
    void deleteByStudyId(String studyId);
}
