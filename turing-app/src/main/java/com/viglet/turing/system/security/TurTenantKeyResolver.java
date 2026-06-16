package com.viglet.turing.system.security;

import com.viglet.core.crypto.VigletTenantKeyResolver;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.tenant.TurTenantContext;

/**
 * Bridges Turing's {@link TurTenantContext} onto the {@code viglet-core}
 * {@link VigletTenantKeyResolver} so the lifted crypto primitive can derive
 * per-tenant keys without depending on any Turing type (Block Q / T368).
 *
 * <p>The default tenant ({@link TurTenant#DEFAULT_TENANT_ID}) maps to the legacy
 * global key; every other tenant gets its own derived key.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurTenantKeyResolver implements VigletTenantKeyResolver {

    private final TurTenantContext tenantContext;

    public TurTenantKeyResolver(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public String currentTenantId() {
        return tenantContext != null ? tenantContext.resolveCurrentTenant() : null;
    }

    @Override
    public boolean isDefaultTenant(String tenantId) {
        return TurTenant.DEFAULT_TENANT_ID.equals(tenantId);
    }
}
