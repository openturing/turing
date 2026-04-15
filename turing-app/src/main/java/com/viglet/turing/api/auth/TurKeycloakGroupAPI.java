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
package com.viglet.turing.api.auth;

import com.viglet.turing.service.keycloak.TurKeycloakAdminService;
import com.viglet.turing.service.keycloak.TurKeycloakGroupDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of groups sourced from the configured Keycloak realm.
 * The single-group endpoint also returns the realm members associated
 * with that group so the admin UI can render the membership list.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Secured("ROLE_ADMIN")
@RestController
@RequestMapping("/api/v2/keycloak/groups")
@Tag(name = "Keycloak Groups", description = "Read-only Keycloak groups API")
public class TurKeycloakGroupAPI {

    private final TurKeycloakAdminService keycloakAdminService;

    public TurKeycloakGroupAPI(TurKeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    @GetMapping
    public ResponseEntity<List<TurKeycloakGroupDto>> list() {
        if (!keycloakAdminService.isEnabled()) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.ok(keycloakAdminService.listGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurKeycloakGroupDto> get(@PathVariable String id) {
        if (!keycloakAdminService.isEnabled()) {
            return ResponseEntity.status(409).build();
        }
        return keycloakAdminService.findGroupById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
