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
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.tenant.TurTenant;

/**
 * T268 / §XIV.4.2 — prefixes search-engine core / index names with the current
 * tenant so two tenants' same-named cores ({@code site_en}, {@code rag_<id>},
 * {@code intent_<id>}) never collide on a shared Solr/ES/Lucene instance.
 *
 * <p>The prefix is {@code t<shortId>_} where {@code shortId} is the first 8 hex
 * chars of the tenant id. When tenancy is off — or the tenant is the immutable
 * {@code DEFAULT} — the base name is returned <em>unchanged</em>, so existing
 * single-tenant cores keep their exact names and nothing needs re-provisioning.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurTenantCoreNaming {

    private static final int TENANT_SHORT_ID_LENGTH = 8;

    private final TurTenantContext tenantContext;

    public TurTenantCoreNaming(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /** Prefix {@code baseCoreName} with the current tenant (no-op when off/DEFAULT). */
    public String scoped(String baseCoreName) {
        String prefix = currentPrefix();
        return prefix.isEmpty() ? baseCoreName : prefix + baseCoreName;
    }

    /** The current tenant's core-name prefix ({@code ""} when off or DEFAULT). */
    public String currentPrefix() {
        if (!tenantContext.isTenancyEnabled()) {
            return "";
        }
        String tenant = tenantContext.resolveCurrentTenant();
        if (!StringUtils.hasText(tenant) || TurTenant.DEFAULT_TENANT_ID.equals(tenant)) {
            return "";
        }
        String shortId = tenant.replace("-", "");
        if (shortId.length() > TENANT_SHORT_ID_LENGTH) {
            shortId = shortId.substring(0, TENANT_SHORT_ID_LENGTH);
        }
        return "t" + shortId + "_";
    }
}
