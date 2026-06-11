/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.tenant;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.tenant.TurTenantResolutionFilter;
import com.viglet.turing.tenant.TurTenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * T265 / §XIV.3.3 — "my tenants" + current-tenant switch for users who belong to
 * more than one tenant.
 *
 * <ul>
 *   <li>{@code GET /api/tenants/mine} — the caller's active tenants.</li>
 *   <li>{@code POST /api/tenants/{slug}/switch} — set the active tenant on the
 *       HTTP session (the attribute {@link TurTenantResolutionFilter} reads at
 *       priority 2). Rejected with 403 unless the caller is an active member.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Multi-tenant membership + switch")
public class TurTenantAPI {

    private final TurTenantService turTenantService;
    private final TurTenantRepository turTenantRepository;

    public TurTenantAPI(TurTenantService turTenantService, TurTenantRepository turTenantRepository) {
        this.turTenantService = turTenantService;
        this.turTenantRepository = turTenantRepository;
    }

    @Operation(summary = "List the tenants the current user is an active member of")
    @GetMapping("/mine")
    public List<TurTenantResponse> myTenants(Principal principal) {
        if (principal == null) {
            return List.of();
        }
        return turTenantService.tenantsOf(principal.getName()).stream()
                .map(TurTenantResponse::of)
                .toList();
    }

    @Operation(summary = "Switch the active tenant for the current session")
    @PostMapping("/{slug}/switch")
    public TurTenantResponse switchTenant(@PathVariable String slug, Principal principal,
            HttpServletRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        TurTenant tenant = turTenantRepository.findBySlug(turTenantService.normalizeSlug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
        if (!turTenantService.isActiveMember(tenant.getId(), principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this tenant");
        }
        request.getSession(true)
                .setAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR, tenant.getId());
        return TurTenantResponse.of(tenant);
    }
}
