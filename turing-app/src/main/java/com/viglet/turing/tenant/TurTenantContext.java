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

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * T258 / §XIV.2.2 — Turing's request-scoped current-tenant holder: the
 * {@code viglet-core} tenancy kernel ({@link VigletTenantContext}) wired with
 * Turing's {@code turing.tenancy.enabled} flag and the {@code DEFAULT} tenant
 * constant.
 *
 * <p>The framework-level behaviour now lives in {@code viglet-core-tenancy}
 * (Block Q / T370). This subclass preserves Turing's wiring exactly — the flag is
 * read from {@code turing.tenancy.*} and the off/unresolved tenant resolves to
 * {@link TurTenant#DEFAULT_TENANT_ID} — so single-tenant installs are unchanged.
 * As a {@code @Component} it satisfies the core auto-configuration's
 * {@code @ConditionalOnMissingBean}, so the generic {@code viglet.tenancy.*} bean
 * never activates here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurTenantContext extends VigletTenantContext {

    public TurTenantContext(TurConfigProperties configProperties) {
        super(configProperties.getTenancy().isEnabled(), TurTenant.DEFAULT_TENANT_ID);
    }
}
