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

import com.viglet.turing.persistence.model.research.TurResearchInsightSnapshot;

/**
 * Spring Data repository for {@link TurResearchInsightSnapshot} (Block AW /
 * §XLVI.4, T729). The ordered series per study is the insight-drift surface.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurResearchInsightSnapshotRepository
        extends JpaRepository<TurResearchInsightSnapshot, String> {

    List<TurResearchInsightSnapshot> findByStudy_IdOrderByCapturedAtAsc(String studyId);
}
