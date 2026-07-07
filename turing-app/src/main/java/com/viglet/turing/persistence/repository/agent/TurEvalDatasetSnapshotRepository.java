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

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurEvalDatasetSnapshot;

/**
 * T598 / §XXXIII.13 — repository for immutable dataset version snapshots.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurEvalDatasetSnapshotRepository extends JpaRepository<TurEvalDatasetSnapshot, String> {

    List<TurEvalDatasetSnapshot> findByDatasetIdOrderByVersionDesc(@Param("datasetId") String datasetId);

    Optional<TurEvalDatasetSnapshot> findByDatasetIdAndVersion(
            @Param("datasetId") String datasetId, @Param("version") int version);
}
