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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for the auto-configured {@link VigletSecretCryptoService}, bound to
 * {@code viglet.crypto.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ConfigurationProperties("viglet.crypto")
public class VigletCryptoProperties {

    /** Master key material. When blank, the dev fallback is used outside production. */
    private String key;

    /** Insecure fallback used only when {@link #key} is blank and not in production. */
    private String devFallbackKey = "viglet-dev-insecure-default-key";

    /** Salt prefix for per-tenant key derivation; keep stable across restarts. */
    private String tenantSaltPrefix = VigletSecretCryptoService.DEFAULT_TENANT_SALT_PREFIX;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDevFallbackKey() {
        return devFallbackKey;
    }

    public void setDevFallbackKey(String devFallbackKey) {
        this.devFallbackKey = devFallbackKey;
    }

    public String getTenantSaltPrefix() {
        return tenantSaltPrefix;
    }

    public void setTenantSaltPrefix(String tenantSaltPrefix) {
        this.tenantSaltPrefix = tenantSaltPrefix;
    }
}
