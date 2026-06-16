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
package com.viglet.core.crypto;

import java.util.Arrays;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configures a ready-to-use {@link VigletSecretCryptoService} from
 * {@code viglet.crypto.*}. Both beans are {@link ConditionalOnMissingBean}, so a
 * product that declares its own (e.g. Turing's {@code TurSecretCryptoService},
 * which wires its own tenant context and legacy key property) wins.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@AutoConfiguration
@EnableConfigurationProperties(VigletCryptoProperties.class)
public class VigletCryptoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VigletCryptoAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public VigletTenantKeyResolver vigletTenantKeyResolver() {
        return VigletTenantKeyResolver.SINGLE_TENANT;
    }

    @Bean
    @ConditionalOnMissingBean
    public VigletSecretCryptoService vigletSecretCryptoService(VigletCryptoProperties properties,
            VigletTenantKeyResolver tenantResolver, Environment environment) {
        Supplier<String> keyMaterial = new Supplier<>() {
            private boolean warned = false;

            @Override
            public String get() {
                String material = properties.getKey();
                if (material == null || material.isBlank()) {
                    if (isProductionProfileActive(environment)) {
                        throw new IllegalStateException("viglet.crypto.key must be configured in production");
                    }
                    if (!warned) {
                        warned = true;
                        log.warn("Using insecure fallback key for secret encryption outside production");
                    }
                    material = properties.getDevFallbackKey();
                }
                return material;
            }
        };
        return new VigletSecretCryptoService(keyMaterial, properties.getTenantSaltPrefix(), tenantResolver);
    }

    private static boolean isProductionProfileActive(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
