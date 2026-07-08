/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.console;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileRequest;
import com.viglet.turing.sn.facet.TurSNSiteFacetProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic Navigation Faceted Field ordering and facet ordering profiles.
 * <p>
 * The legacy endpoints ({@code GET /} and {@code PUT /ordering}) operate on the
 * site's default profile, preserving backward compatibility. The
 * {@code /profile} sub-resources manage named profiles and their orderings.
 * <p>
 * All facet-position persistence (including the LazyInitialization-safe custom
 * facet loading and the {@code @CacheEvict}-triggering per-item save) lives in
 * {@link TurSNSiteFacetProfileService}.
 *
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/facet")
@Tag(name = "Semantic Navigation Faceted Field", description = "Semantic Navigation Faceted Field API")
public class TurSNSiteFacetedFieldAPI {

    private static final String FACET_ORDER_CACHE = "findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition";

    private final TurSNSiteFacetProfileService turSNSiteFacetProfileService;

    public TurSNSiteFacetedFieldAPI(TurSNSiteFacetProfileService turSNSiteFacetProfileService) {
        this.turSNSiteFacetProfileService = turSNSiteFacetProfileService;
    }

    // ---------------------------------------------------- default profile (legacy)

    @Operation(summary = "Semantic Navigation Site Faceted Field List (default profile)")
    @GetMapping
    public List<TurSNSiteFacetOrderingDto> turSNSiteFacetdFieldExtList(@PathVariable String snSiteId) {
        return turSNSiteFacetProfileService.getDefaultOrdering(snSiteId);
    }

    @Operation(summary = "Update the Faceted Field Ordering of the default profile")
    @PutMapping("/ordering")
    @CacheEvict(value = { FACET_ORDER_CACHE }, allEntries = true)
    public List<TurSNSiteFacetOrderingDto> turSNSiteFieldUpdate(@PathVariable String snSiteId,
            @RequestBody List<TurSNSiteFacetOrderingDto> turSNSiteFacetOrderings) {
        return turSNSiteFacetProfileService.saveDefaultOrdering(snSiteId, turSNSiteFacetOrderings);
    }

    // ------------------------------------------------------------- profiles (CRUD)

    @Operation(summary = "List facet ordering profiles")
    @GetMapping("/profile")
    public List<TurSNSiteFacetProfileDto> profileList(@PathVariable String snSiteId) {
        return turSNSiteFacetProfileService.list(snSiteId);
    }

    @Operation(summary = "Create a facet ordering profile")
    @PostMapping("/profile")
    @ResponseStatus(HttpStatus.CREATED)
    public TurSNSiteFacetProfileDto profileCreate(@PathVariable String snSiteId,
            @RequestBody TurSNSiteFacetProfileRequest profile) {
        return turSNSiteFacetProfileService.create(snSiteId, profile);
    }

    @Operation(summary = "Update a facet ordering profile")
    @PutMapping("/profile/{profileId}")
    public TurSNSiteFacetProfileDto profileUpdate(@PathVariable String snSiteId,
            @PathVariable String profileId, @RequestBody TurSNSiteFacetProfileRequest profile) {
        return turSNSiteFacetProfileService.update(snSiteId, profileId, profile);
    }

    @Operation(summary = "Mark a facet ordering profile as the default")
    @PutMapping("/profile/{profileId}/default")
    @CacheEvict(value = { FACET_ORDER_CACHE }, allEntries = true)
    public List<TurSNSiteFacetProfileDto> profileSetDefault(@PathVariable String snSiteId,
            @PathVariable String profileId) {
        return turSNSiteFacetProfileService.setDefault(snSiteId, profileId);
    }

    @Operation(summary = "Delete a facet ordering profile")
    @DeleteMapping("/profile/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void profileDelete(@PathVariable String snSiteId, @PathVariable String profileId) {
        turSNSiteFacetProfileService.delete(snSiteId, profileId);
    }

    // -------------------------------------------------------- ordering per profile

    @Operation(summary = "List the facet ordering of a profile")
    @GetMapping("/profile/{profileId}/ordering")
    public List<TurSNSiteFacetOrderingDto> profileOrdering(@PathVariable String snSiteId,
            @PathVariable String profileId) {
        return turSNSiteFacetProfileService.getOrdering(snSiteId, profileId);
    }

    @Operation(summary = "Update the facet ordering of a profile")
    @PutMapping("/profile/{profileId}/ordering")
    @CacheEvict(value = { FACET_ORDER_CACHE }, allEntries = true)
    public List<TurSNSiteFacetOrderingDto> profileOrderingUpdate(@PathVariable String snSiteId,
            @PathVariable String profileId,
            @RequestBody List<TurSNSiteFacetOrderingDto> turSNSiteFacetOrderings) {
        return turSNSiteFacetProfileService.saveOrdering(snSiteId, profileId, turSNSiteFacetOrderings);
    }
}
