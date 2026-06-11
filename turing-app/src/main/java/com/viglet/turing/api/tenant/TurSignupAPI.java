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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.tenant.TurTenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T264 / §XIV.3.2 — self-service tenant signup.
 *
 * <p>{@code POST /api/signup} creates a {@link TurTenant} and an OWNER
 * membership. The owner is the authenticated principal when present; otherwise
 * the {@code username} from the request body (brand-new self-registration).
 * Idempotent and slug-validated by {@link TurTenantService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/signup")
@Tag(name = "Tenant Signup", description = "Self-service multi-tenant signup")
public class TurSignupAPI {

    private final TurTenantService turTenantService;

    public TurSignupAPI(TurTenantService turTenantService) {
        this.turTenantService = turTenantService;
    }

    public record TurTenantSignupRequest(String slug, String name, String username) {
    }

    @Operation(summary = "Create a new tenant with an OWNER membership")
    @PostMapping
    public ResponseEntity<TurTenantResponse> signup(@RequestBody TurTenantSignupRequest request,
            Principal principal) {
        String owner = principal != null ? principal.getName() : request.username();
        if (!StringUtils.hasText(owner)) {
            return ResponseEntity.badRequest().build();
        }
        TurTenant tenant = turTenantService.signup(request.slug(), request.name(), owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(TurTenantResponse.of(tenant));
    }
}
