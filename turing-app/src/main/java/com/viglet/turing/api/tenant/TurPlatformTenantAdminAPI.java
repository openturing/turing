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
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.tenant.TurPlatformAdminService;
import com.viglet.turing.tenant.TurTenantResolutionFilter;
import com.viglet.turing.tenant.TurTenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * T279 / §XIV.6.2 — platform-admin tenant console: list, suspend, reactivate
 * and impersonate tenants. Every endpoint requires
 * {@link TurPlatformAdminService#ROLE_PLATFORM_ADMIN}.
 *
 * <p>Impersonation sets the session's active tenant to a tenant the admin is
 * NOT a member of — the resolution filter (T259) honours this because the
 * principal holds the platform-admin role. Reads of that tenant's owned data
 * are audited by {@link TurPlatformAdminService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/platform/tenants")
@Secured(VigletPlatformAdminService.ROLE_PLATFORM_ADMIN)
@Tag(name = "Platform Tenant Admin", description = "Cross-tenant ops for platform admins")
public class TurPlatformTenantAdminAPI {

    private final TurTenantService turTenantService;
    private final TurTenantRepository turTenantRepository;

    public TurPlatformTenantAdminAPI(TurTenantService turTenantService,
            TurTenantRepository turTenantRepository) {
        this.turTenantService = turTenantService;
        this.turTenantRepository = turTenantRepository;
    }

    @Operation(summary = "List all tenants")
    @GetMapping
    public List<TurTenantResponse> list() {
        return turTenantService.findAll().stream().map(TurTenantResponse::of).toList();
    }

    @Operation(summary = "Suspend a tenant (blocks its members' access)")
    @PostMapping("/{id}/suspend")
    public TurTenantResponse suspend(@PathVariable String id) {
        return TurTenantResponse.of(turTenantService.setStatus(id, TurTenantStatus.SUSPENDED));
    }

    @Operation(summary = "Reactivate a suspended tenant")
    @PostMapping("/{id}/activate")
    public TurTenantResponse activate(@PathVariable String id) {
        return TurTenantResponse.of(turTenantService.setStatus(id, TurTenantStatus.ACTIVE));
    }

    @Operation(summary = "Impersonate a tenant for this session (audited)")
    @PostMapping("/{id}/impersonate")
    public TurTenantResponse impersonate(@PathVariable String id, Principal principal,
            HttpServletRequest request) {
        TurTenant tenant = turTenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
        request.getSession(true)
                .setAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR, tenant.getId());
        return TurTenantResponse.of(tenant);
    }
}
