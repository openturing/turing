/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.sn.console;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.coverage.TurSNFieldCoverageReport;
import com.viglet.turing.sn.coverage.TurSNFieldCoverageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * T388 — per-field coverage / completeness observability for a Semantic
 * Navigation site. Reports the percentage of indexed documents that populate
 * each field, making grounding visible and pinpointing under-filled sources.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/field-coverage")
@Tag(name = "Semantic Navigation Field Coverage",
        description = "Per-field completeness / data-quality observability")
@RequiredArgsConstructor
public class TurSNSiteFieldCoverageAPI {

    private final TurSNSiteRepository snSiteRepository;
    private final TurSNFieldCoverageService fieldCoverageService;

    @Operation(summary = "Per-field coverage report for a Semantic Navigation site")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<TurSNFieldCoverageReport> coverage(@PathVariable String snSiteId) {
        // T488 / §XXVIII.3 — read inside a transaction so the coverage service can
        // resolve the site's lazy associations through the open session.
        TurSNSite site = snSiteRepository.findById(snSiteId).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(fieldCoverageService.coverage(site));
    }
}
