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

import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlatformAdminService;

/**
 * T266 / §XIV.3.4 — Turing's cross-tenant privilege gate: the {@code viglet-core}
 * tenancy kernel ({@link VigletPlatformAdminService}) exposed as a Turing
 * {@code @Service} bean.
 *
 * <p>The audited {@code runForTenant} / {@code runAsSystem} primitives and the
 * {@link #ROLE_PLATFORM_ADMIN} authority check now live in
 * {@code viglet-core-tenancy} (Block Q / T370). This subclass exists only to be a
 * Turing-owned bean (satisfying the core {@code @ConditionalOnMissingBean}) and
 * to keep the {@code TurPlatformAdminService.ROLE_PLATFORM_ADMIN} reference — used
 * in {@code @Secured} annotations and authority resolution across the app —
 * compiling unchanged. How the authority is <em>granted</em> (T366 Keycloak realm
 * role → {@code ROLE_PLATFORM_ADMIN}) stays in {@code TurAuthorityResolver}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurPlatformAdminService extends VigletPlatformAdminService {

    public TurPlatformAdminService(TurTenantContext tenantContext) {
        super(tenantContext);
    }
}
