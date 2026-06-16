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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.core.tenancy.VigletTenantSlugValidator;

/**
 * T396 / §XIV.9 — Turing's tenancy wiring onto the expanded shared
 * {@code viglet-core-tenancy} surface. The shared services
 * ({@code VigletTenantMembershipService}, {@code VigletQuotaService},
 * {@code VigletTenantTeardownService}) auto-configure themselves the moment
 * Turing's SPI beans ({@link TurVigletTenantStore}, {@link TurVigletTenantResolver},
 * {@link TurVigletResourceCounter}, {@link TurVigletTenantPurger}) are present;
 * the only product policy this configuration injects is the slug validator's
 * extra reserved name.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration(proxyBeanMethods = false)
public class TurTenancyConfiguration {

    /**
     * The shared slug validator with Turing's one extra reserved label
     * ({@code turing}) on top of the platform-neutral defaults — so signup rejects
     * {@code turing} as a tenant slug exactly as the legacy {@code TurTenantService}
     * did. Overrides the auto-config default ({@code @ConditionalOnMissingBean}).
     */
    @Bean
    public VigletTenantSlugValidator vigletTenantSlugValidator() {
        return new VigletTenantSlugValidator(Set.of("turing"));
    }
}
