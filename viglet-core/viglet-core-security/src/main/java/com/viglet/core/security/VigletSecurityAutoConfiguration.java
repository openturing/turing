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
package com.viglet.core.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the framework-level security beans from {@code viglet-core-security}.
 * Every bean is {@link ConditionalOnMissingBean}, so a product that supplies its
 * own — including a subclass of one of these types (e.g. Turing's
 * {@code TurAuthenticationEntryPoint extends VigletAuthenticationEntryPoint}) —
 * wins and the generic bean never activates there.
 *
 * <p>This deliberately does not assemble a {@code SecurityFilterChain}: the
 * filter-chain wiring (which paths are public, how OIDC login is set up, the
 * CSRF ignore-list) is product-specific and stays in each product.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(VigletSecurityProperties.class)
public class VigletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VigletAuthenticationEntryPoint vigletAuthenticationEntryPoint() {
        return new VigletAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean
    public VigletCsrfController vigletCsrfController() {
        return new VigletCsrfController();
    }
}
