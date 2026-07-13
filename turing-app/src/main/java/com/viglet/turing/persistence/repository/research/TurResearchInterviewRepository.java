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

import com.viglet.turing.persistence.model.research.TurResearchInterview;

/**
 * Spring Data repository for {@link TurResearchInterview} transcripts
 * (Block AW / §XLVI.2, T721). Interviews cascade-delete with their study via the
 * FK, so no explicit bulk delete is needed here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurResearchInterviewRepository
        extends JpaRepository<TurResearchInterview, String> {

    List<TurResearchInterview> findByStudy_IdOrderByPersonaIdAsc(String studyId);

    long countByStudy_Id(String studyId);
}
