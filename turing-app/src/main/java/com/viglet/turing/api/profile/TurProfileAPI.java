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
package com.viglet.turing.api.profile;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.profile.bean.TurProfileRequest;
import com.viglet.turing.profile.TurProfileFacetOrderingService;
import com.viglet.turing.profile.TurProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * CRUD of the generic (global) profiles, managed under the console "Management"
 * section (spec 002). The default profile ("Padrão") is protected: it cannot be
 * renamed nor deleted.
 * <p>
 * Per-site facet ordering configurations are attached to a profile through the
 * Semantic Navigation facet endpoints, not here.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Slf4j
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Generic Profile API")
public class TurProfileAPI {

    private final TurProfileService turProfileService;
    private final TurProfileFacetOrderingService turProfileFacetOrderingService;

    public TurProfileAPI(TurProfileService turProfileService,
            TurProfileFacetOrderingService turProfileFacetOrderingService) {
        this.turProfileService = turProfileService;
        this.turProfileFacetOrderingService = turProfileFacetOrderingService;
    }

    @Operation(summary = "Profile List")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "PROFILE_VIEW" })
    public List<TurProfileDto> profileList() {
        return turProfileService.list();
    }

    @Operation(summary = "Show a Profile")
    @GetMapping("/{profileId}")
    @Secured({ "ROLE_ADMIN", "PROFILE_VIEW" })
    public TurProfileDto profileGet(@PathVariable String profileId) {
        return turProfileService.get(profileId);
    }

    @Operation(summary = "Create a Profile")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Secured({ "ROLE_ADMIN", "PROFILE_CREATE" })
    public TurProfileDto profileCreate(@RequestBody TurProfileRequest profile) {
        return turProfileService.create(profile);
    }

    @Operation(summary = "Update a Profile")
    @PutMapping("/{profileId}")
    @Secured({ "ROLE_ADMIN", "PROFILE_EDIT" })
    public TurProfileDto profileUpdate(@PathVariable String profileId,
            @RequestBody TurProfileRequest profile) {
        return turProfileService.update(profileId, profile);
    }

    @Operation(summary = "Mark a Profile as the default")
    @PutMapping("/{profileId}/default")
    @Secured({ "ROLE_ADMIN", "PROFILE_EDIT" })
    public List<TurProfileDto> profileSetDefault(@PathVariable String profileId) {
        // Delegates to the ordering service so the legacy facetPosition mirror is
        // rewritten on every site where the new default has an ordering (DT-6).
        return turProfileFacetOrderingService.setDefault(profileId);
    }

    @Operation(summary = "Delete a Profile")
    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Secured({ "ROLE_ADMIN", "PROFILE_DELETE" })
    public void profileDelete(@PathVariable String profileId) {
        turProfileService.delete(profileId);
    }
}
