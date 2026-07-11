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
package com.viglet.turing.api.kb;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.kb.TurSNSiteMicrothesaurusService;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusConfigDto;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T670 / §XL (Block AQ) — per-SN-site microthesaurus selection + index config.
 * A site opts into the Knowledge Base by selecting microthesauri and, optionally,
 * tuning the backing field. No selection = legacy indexing (ADR 0003).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/microthesaurus")
@Tag(name = "SN Site Microthesaurus", description = "Per-site Knowledge Base selection & index config")
public class TurSNSiteMicrothesaurusAPI {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteMicrothesaurusService service;

    public TurSNSiteMicrothesaurusAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteMicrothesaurusService service) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.service = service;
    }

    private TurSNSite snSite(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> TurNotFoundException.of("SN Site", snSiteId));
    }

    @Operation(summary = "List the microthesauri selected by this site")
    @GetMapping
    public List<TurSNSiteMicrothesaurusDto> list(@PathVariable String snSiteId) {
        return service.listSelections(snSite(snSiteId));
    }

    @Operation(summary = "Select a microthesaurus for this site")
    @PostMapping
    public TurSNSiteMicrothesaurusDto select(@PathVariable String snSiteId,
            @RequestBody TurSNSiteMicrothesaurusDto dto) {
        return service.select(snSite(snSiteId), dto);
    }

    @Operation(summary = "Enable/disable a selection")
    @PutMapping("/{selectionId}")
    public TurSNSiteMicrothesaurusDto setEnabled(@PathVariable String snSiteId,
            @PathVariable String selectionId, @RequestParam boolean enabled) {
        snSite(snSiteId);
        return service.setSelectionEnabled(selectionId, enabled)
                .orElseThrow(() -> TurNotFoundException.of("Selection", selectionId));
    }

    @Operation(summary = "Remove a selection")
    @DeleteMapping("/{selectionId}")
    public boolean deselect(@PathVariable String snSiteId, @PathVariable String selectionId) {
        snSite(snSiteId);
        return service.deselect(selectionId);
    }

    @Operation(summary = "Get the per-site microthesaurus index config")
    @GetMapping("/config")
    public TurSNSiteMicrothesaurusConfigDto getConfig(@PathVariable String snSiteId) {
        return service.getConfig(snSite(snSiteId));
    }

    @Operation(summary = "Save the per-site microthesaurus index config (provisions the field when enabled)")
    @PutMapping("/config")
    public TurSNSiteMicrothesaurusConfigDto saveConfig(@PathVariable String snSiteId,
            @RequestBody TurSNSiteMicrothesaurusConfigDto dto) {
        return service.saveConfig(snSite(snSiteId), dto);
    }
}
