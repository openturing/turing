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

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletInfraTenantScope;

/**
 * §XIV.5.1 — Turing's tenant-scoping helper for the "bring-your-own-infra"
 * instance entities: the {@code viglet-core} tenancy kernel
 * ({@link VigletInfraTenantScope}) exposed as a Turing {@code @Component} bean.
 *
 * <p>The scoping logic — stamp-on-create, scoped list, read-only GLOBAL pool
 * (T372) and by-id visibility (T365) — now lives in {@code viglet-core-tenancy}
 * (Block Q / T370) and operates on {@code VigletTenantOwnedInfra}, which the six
 * Turing BYO-infra entities implement directly. This subclass exists only to be a
 * Turing-owned bean (satisfying the core {@code @ConditionalOnMissingBean}); all
 * call sites are unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurInfraTenantScope extends VigletInfraTenantScope {

    public TurInfraTenantScope(TurTenantContext tenantContext,
            TurPlatformAdminService platformAdminService) {
        super(tenantContext, platformAdminService);
    }
}
