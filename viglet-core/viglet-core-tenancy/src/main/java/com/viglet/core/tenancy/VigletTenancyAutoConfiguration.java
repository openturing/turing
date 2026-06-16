/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.tenancy;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configures the tenancy kernel from {@code viglet.tenancy.*}. Every bean is
 * {@link ConditionalOnMissingBean}, so a product that declares its own (e.g.
 * Turing's {@code TurTenantContext} / {@code TurPlatformAdminService} /
 * {@code TurInfraTenantScope}, wired from {@code turing.tenancy.*}) wins and the
 * generic {@code viglet.tenancy.*} beans never activate.
 *
 * <p>The Hibernate discriminator wiring is split into a nested configuration
 * guarded by {@link ConditionalOnClass} so this module works without JPA on the
 * classpath (a product wanting only the context + scope helper).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@AutoConfiguration
@EnableConfigurationProperties(VigletTenancyProperties.class)
public class VigletTenancyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VigletTenantContext vigletTenantContext(VigletTenancyProperties properties) {
        return new VigletTenantContext(properties.isEnabled(), properties.getDefaultTenantId());
    }

    @Bean
    @ConditionalOnMissingBean
    public VigletPlatformAdminService vigletPlatformAdminService(VigletTenantContext tenantContext) {
        return new VigletPlatformAdminService(tenantContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public VigletInfraTenantScope vigletInfraTenantScope(VigletTenantContext tenantContext,
            VigletPlatformAdminService platformAdminService) {
        return new VigletInfraTenantScope(tenantContext, platformAdminService);
    }

    /**
     * Wires Hibernate's discriminator multi-tenancy to {@link VigletTenantContext}.
     * Any entity carrying {@code @org.hibernate.annotations.TenantId} enables
     * discriminator multi-tenancy: inserts are stamped and reads gain
     * {@code WHERE tenantId = ?} automatically, driven by the resolver registered
     * here. The resolver yields the default tenant whenever the feature flag is
     * off — so single-tenant installs see one constant discriminator.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ CurrentTenantIdentifierResolver.class, HibernatePropertiesCustomizer.class })
    static class HibernateMultiTenancyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CurrentTenantIdentifierResolver<String> vigletCurrentTenantIdentifierResolver(
                VigletTenantContext tenantContext) {
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
        @ConditionalOnMissingBean(name = "vigletTenantResolverPropertiesCustomizer")
        HibernatePropertiesCustomizer vigletTenantResolverPropertiesCustomizer(
                CurrentTenantIdentifierResolver<String> vigletCurrentTenantIdentifierResolver) {
            return properties -> properties.put(
                    MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
                    vigletCurrentTenantIdentifierResolver);
        }
    }
}
