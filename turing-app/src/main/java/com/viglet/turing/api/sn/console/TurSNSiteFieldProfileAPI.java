/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurProfileFieldConfigRequest;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto;
import com.viglet.turing.profile.TurProfileFieldConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Per-profile facet configuration of a site's fields (spec 003): the "Campos"
 * console page selects a profile and edits, per field, the facet settings that
 * are specific to that profile (or inherit the global value).
 * <p>
 * The <b>default</b> profile writes straight to the global field configuration
 * (see {@link TurProfileFieldConfigService}); the global-only editing path stays
 * on {@link TurSNSiteFieldExtAPI} ({@code /api/sn/{snSiteId}/field/ext}).
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/field/profile/{profileId}")
@Tag(name = "Semantic Navigation Field Profile Config",
        description = "Per-profile facet configuration of a site's fields")
public class TurSNSiteFieldProfileAPI {

    private final TurProfileFieldConfigService turProfileFieldConfigService;

    public TurSNSiteFieldProfileAPI(TurProfileFieldConfigService turProfileFieldConfigService) {
        this.turProfileFieldConfigService = turProfileFieldConfigService;
    }

    @Operation(summary = "List fields with their effective facet config for a profile")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "SN_VIEW" })
    public List<TurSNSiteFieldProfileConfigDto> list(@PathVariable String snSiteId,
            @PathVariable String profileId) {
        return turProfileFieldConfigService.getEffectiveConfigs(snSiteId, profileId);
    }

    @Operation(summary = "Upsert a field's facet override for a profile")
    @PutMapping("/{fieldExtId}")
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public TurSNSiteFieldProfileConfigDto save(@PathVariable String snSiteId,
            @PathVariable String profileId, @PathVariable String fieldExtId,
            @RequestBody TurProfileFieldConfigRequest request) {
        return turProfileFieldConfigService.saveOverride(snSiteId, profileId, fieldExtId, request);
    }

    @Operation(summary = "Clear a field's facet override for a profile (back to inheriting global)")
    @DeleteMapping("/{fieldExtId}")
    @Secured({ "ROLE_ADMIN", "SN_EDIT" })
    public boolean clear(@PathVariable String snSiteId, @PathVariable String profileId,
            @PathVariable String fieldExtId) {
        return turProfileFieldConfigService.clearOverride(snSiteId, profileId, fieldExtId);
    }
}
