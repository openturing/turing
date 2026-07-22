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
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.profile.TurProfileFacetOrderingService;
import com.viglet.turing.profile.TurProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic Navigation Faceted Field ordering (spec 002).
 * <p>
 * Profiles are <b>global</b> and managed under {@code /api/profile}
 * ("Management" section); here the site only <b>selects</b> a profile and
 * configures its facet ordering for this site — one configuration per
 * (profile × site). The legacy endpoints ({@code GET /} and
 * {@code PUT /ordering}) operate on the default profile's ordering for the
 * site, preserving backward compatibility.
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

    private final TurProfileService turProfileService;
    private final TurProfileFacetOrderingService turProfileFacetOrderingService;

    public TurSNSiteFacetedFieldAPI(TurProfileService turProfileService,
            TurProfileFacetOrderingService turProfileFacetOrderingService) {
        this.turProfileService = turProfileService;
        this.turProfileFacetOrderingService = turProfileFacetOrderingService;
    }

    // ---------------------------------------------------- default profile (legacy)

    @Operation(summary = "Semantic Navigation Site Faceted Field List (default profile)")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "SN_VIEW" })
    public List<TurSNSiteFacetOrderingDto> turSNSiteFacetdFieldExtList(@PathVariable String snSiteId) {
        return turProfileFacetOrderingService.getDefaultOrdering(snSiteId);
    }

    @Operation(summary = "Update the Faceted Field Ordering of the default profile")
    @PutMapping("/ordering")
    @CacheEvict(value = { FACET_ORDER_CACHE }, allEntries = true)
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public List<TurSNSiteFacetOrderingDto> turSNSiteFieldUpdate(@PathVariable String snSiteId,
            @RequestBody List<TurSNSiteFacetOrderingDto> turSNSiteFacetOrderings) {
        return turProfileFacetOrderingService.saveDefaultOrdering(snSiteId, turSNSiteFacetOrderings);
    }

    // ------------------------------------------- global profiles (selection only)

    @Operation(summary = "List the global profiles available for selection on this site")
    @GetMapping("/profile")
    @Secured({ "ROLE_ADMIN", "SN_VIEW" })
    public List<TurProfileDto> profileList(@PathVariable String snSiteId) {
        return turProfileService.list();
    }

    // -------------------------------------------- ordering per (profile × site)

    @Operation(summary = "List the facet ordering of a profile for this site")
    @GetMapping("/profile/{profileId}/ordering")
    @Secured({ "ROLE_ADMIN", "SN_VIEW" })
    public List<TurSNSiteFacetOrderingDto> profileOrdering(@PathVariable String snSiteId,
            @PathVariable String profileId) {
        return turProfileFacetOrderingService.getOrdering(snSiteId, profileId);
    }

    @Operation(summary = "Update the facet ordering of a profile for this site")
    @PutMapping("/profile/{profileId}/ordering")
    @CacheEvict(value = { FACET_ORDER_CACHE }, allEntries = true)
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public List<TurSNSiteFacetOrderingDto> profileOrderingUpdate(@PathVariable String snSiteId,
            @PathVariable String profileId,
            @RequestBody List<TurSNSiteFacetOrderingDto> turSNSiteFacetOrderings) {
        return turProfileFacetOrderingService.saveOrdering(snSiteId, profileId,
                turSNSiteFacetOrderings);
    }
}
