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

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T260 / §XIV.2.4 — wires Hibernate's discriminator multi-tenancy to
 * {@link TurTenantContext}.
 *
 * <p>Any entity carrying {@code @org.hibernate.annotations.TenantId} (the pilot
 * is {@code TurSNSite}; T261 fans it out) enables discriminator multi-tenancy
 * in Hibernate: inserts are stamped and reads gain {@code WHERE tenantId = ?}
 * automatically, driven by the {@link CurrentTenantIdentifierResolver}
 * registered here. The resolver delegates to
 * {@link TurTenantContext#resolveCurrentTenant()}, which yields
 * {@code DEFAULT} whenever the feature flag is off — so single-tenant installs
 * see one constant discriminator and behave exactly as before.
 *
 * <p>The resolver is registered explicitly via a
 * {@link HibernatePropertiesCustomizer} (rather than relying on bean
 * auto-detection) so the wiring is unambiguous across Boot/Hibernate versions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class TurHibernateMultiTenancyConfig {

    @Bean
    public CurrentTenantIdentifierResolver<String> turCurrentTenantIdentifierResolver(
            TurTenantContext tenantContext) {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public String resolveCurrentTenantIdentifier() {
                return tenantContext.resolveCurrentTenant();
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                // Sessions are short-lived and bound per request; no cross-tenant
                // session reuse to validate.
                return false;
            }
        };
    }

    @Bean
    public HibernatePropertiesCustomizer turTenantResolverPropertiesCustomizer(
            CurrentTenantIdentifierResolver<String> turCurrentTenantIdentifierResolver) {
        return properties -> properties.put(
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                turCurrentTenantIdentifierResolver);
    }
}
