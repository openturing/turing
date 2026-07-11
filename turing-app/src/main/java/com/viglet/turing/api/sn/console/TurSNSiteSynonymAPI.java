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

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymAlgoliaImportRequest;
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymSupportDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSESynonymApplyResult;
import com.viglet.turing.sn.synonym.TurSNSynonymApplyService;
import com.viglet.turing.sn.synonym.TurSNSynonymImportService;
import com.viglet.turing.sn.synonym.TurSNSynonymService;
import com.viglet.turing.sn.synonym.TurSynonymMiningService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T662 / §XXXIX (Block AP) — management REST API for the engine-agnostic
 * synonym store: CRUD + batch upsert + substring search. Secured by the URL
 * rules in the security config (admin console), matching the sibling
 * {@code TurSNSiteSpotlightAPI}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/synonym")
@Tag(name = "Semantic Navigation Synonym", description = "Semantic Navigation Synonym API")
public class TurSNSiteSynonymAPI {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSynonymService turSNSynonymService;
    private final TurSNSynonymApplyService turSNSynonymApplyService;
    private final TurSNSynonymImportService turSNSynonymImportService;
    private final TurSynonymMiningService turSynonymMiningService;

    public TurSNSiteSynonymAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSynonymService turSNSynonymService,
            TurSNSynonymApplyService turSNSynonymApplyService,
            TurSNSynonymImportService turSNSynonymImportService,
            TurSynonymMiningService turSynonymMiningService) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSynonymService = turSNSynonymService;
        this.turSNSynonymApplyService = turSNSynonymApplyService;
        this.turSNSynonymImportService = turSNSynonymImportService;
        this.turSynonymMiningService = turSynonymMiningService;
    }

    private TurSNSite site(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> TurNotFoundException.of("SN Site", snSiteId));
    }

    @Operation(summary = "List a Semantic Navigation Site's synonyms")
    @GetMapping
    public List<TurSNSynonymDto> list(@PathVariable String snSiteId,
            @RequestParam(required = false) Locale locale,
            @RequestParam(required = false) String q) {
        return turSNSynonymService.list(site(snSiteId), locale, q);
    }

    @Operation(summary = "Show a synonym")
    @GetMapping("/{id}")
    public TurSNSynonymDto get(@PathVariable String snSiteId, @PathVariable String id) {
        site(snSiteId);
        return turSNSynonymService.get(id)
                .orElseThrow(() -> TurNotFoundException.of("Synonym", id));
    }

    @Operation(summary = "Create a synonym")
    @PostMapping
    public TurSNSynonymDto create(@PathVariable String snSiteId,
            @RequestBody TurSNSynonymDto dto) {
        return turSNSynonymService.create(site(snSiteId), dto);
    }

    @Operation(summary = "Update a synonym")
    @PutMapping("/{id}")
    public TurSNSynonymDto update(@PathVariable String snSiteId, @PathVariable String id,
            @RequestBody TurSNSynonymDto dto) {
        site(snSiteId);
        return turSNSynonymService.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Synonym", id));
    }

    @Operation(summary = "Delete a synonym")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String snSiteId, @PathVariable String id) {
        site(snSiteId);
        return turSNSynonymService.delete(id);
    }

    @Operation(summary = "Bulk upsert synonyms (create or update by id)")
    @PostMapping("/batch")
    public List<TurSNSynonymDto> batch(@PathVariable String snSiteId,
            @RequestBody List<TurSNSynonymDto> dtos) {
        return turSNSynonymService.batch(site(snSiteId), dtos);
    }

    @Operation(summary = "Whether the site's engine supports query-time synonyms")
    @GetMapping("/support")
    public TurSNSynonymSupportDto support(@PathVariable String snSiteId) {
        site(snSiteId);
        return turSNSynonymApplyService.support(snSiteId);
    }

    @Operation(summary = "Push synonyms into the search engine (all locales, or one via ?locale=)")
    @PostMapping("/apply")
    public Map<String, TurSESynonymApplyResult> apply(@PathVariable String snSiteId,
            @RequestParam(required = false) Locale locale) {
        site(snSiteId);
        if (locale == null) {
            return turSNSynonymApplyService.applyAll(snSiteId);
        }
        return Map.of(locale.toLanguageTag(), turSNSynonymApplyService.apply(snSiteId, locale));
    }

    @Operation(summary = "Import an Algolia index's synonyms into this site (closes the migration loop)")
    @PostMapping("/import/algolia")
    public List<TurSNSynonymDto> importFromAlgolia(@PathVariable String snSiteId,
            @RequestBody TurSNSynonymAlgoliaImportRequest request) {
        return turSNSynonymImportService.importFromAlgolia(site(snSiteId), request.appId(),
                request.apiKey(), request.host(), request.index(), request.locale());
    }

    @Operation(summary = "Mine candidate synonym sets from the search log (AI-assisted; never auto-applied)")
    @PostMapping("/mine")
    public List<TurSNSynonymDto> mine(@PathVariable String snSiteId,
            @RequestParam Locale locale,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Double threshold) {
        return turSynonymMiningService.mine(site(snSiteId), locale, limit,
                threshold == null ? 0 : threshold);
    }
}
