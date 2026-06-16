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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for the auto-configured tenancy kernel, bound to
 * {@code viglet.tenancy.*}. These drive only the {@link VigletTenancyAutoConfiguration}
 * fallback beans — a product that declares its own {@link VigletTenantContext}
 * (e.g. Turing's {@code TurTenantContext}, wired from {@code turing.tenancy.*})
 * wins via {@code @ConditionalOnMissingBean} and these are never read.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ConfigurationProperties("viglet.tenancy")
public class VigletTenancyProperties {

    /** Master switch for multi-tenancy. Default {@code false} = legacy single-tenant. */
    private boolean enabled = false;

    /**
     * The immutable tenant resolved when tenancy is off, or when on but nothing is
     * bound to the thread. Single-tenant installs see this constant discriminator.
     */
    private String defaultTenantId = "DEFAULT";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }
}
