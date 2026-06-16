/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantResolutionFilter;

/**
 * T259 / §XIV.2.3 — resolves the <em>current tenant</em> for each request and
 * binds it to {@link TurTenantContext} for the request thread.
 *
 * <p>T396 / §XIV.9 — re-homed onto the shared {@link VigletTenantResolutionFilter}:
 * this class is now a thin Turing-named bean that supplies the lifted gate
 * (suspended → 403, non-member → 403, platform-admin bypass, always-clear — the
 * cross-tenant-leak surface that used to be written once per product) with
 * Turing's own resolution sources via {@link TurVigletTenantResolver}. Registered
 * <strong>after</strong> the authentication filters in
 * {@code TurSecurityConfigProduction} / {@code TurMcpSecurityConfig}; the whole
 * filter is a no-op when {@code turing.tenancy.enabled=false}, so a single-tenant
 * install never pays a DB lookup and behaves exactly as before.
 *
 * <p>The resolution-source constants live here (the T265 switch and the platform
 * admin impersonate API write {@link #TENANT_SESSION_ATTR}; the resolver reads
 * these); the resolution <em>logic</em> lives in {@link TurVigletTenantResolver}
 * and the gate in the shared base.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurTenantResolutionFilter extends VigletTenantResolutionFilter {

    /** Header carrying an explicit tenant id/slug (lowest precedence). */
    public static final String TENANT_HEADER = "X-Turing-Tenant";

    /**
     * T334 / §XIV.8.1 — shared-secret header a trusted internal caller sends
     * alongside {@link #TENANT_HEADER} to vouch for the stamped tenant without an
     * end-user membership. Matched against {@code turing.tenancy.internal-token}.
     */
    public static final String INTERNAL_TOKEN_HEADER = "X-Turing-Internal-Token";

    /** HTTP session attribute the T265 switch API writes the active tenant id into. */
    public static final String TENANT_SESSION_ATTR = "TURING_TENANT";

    /** Token claim that selects the active membership for OIDC/JWT principals. */
    public static final String TENANT_CLAIM = "tenant";

    /**
     * Host labels that are never a tenant slug (platform hosts). A subdomain
     * matching one of these is ignored so {@code app.turing.cloud} doesn't resolve
     * to a phantom "app" tenant. Read by {@link TurVigletTenantResolver#subdomain}.
     */
    static final Set<String> RESERVED_SUBDOMAINS = Set.of(
            "www", "app", "api", "admin", "console", "localhost", "turing");

    public TurTenantResolutionFilter(TurTenantContext tenantContext,
            TurVigletTenantStore store,
            TurVigletTenantResolver resolver,
            TurPlatformAdminService platformAdminService) {
        super(tenantContext, store, resolver, platformAdminService);
    }
}
