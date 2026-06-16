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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for the lifted crypto primitive (Block Q / T368).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletSecretCryptoServiceTest {

    private static VigletSecretCryptoService service(String key, VigletTenantKeyResolver resolver) {
        return new VigletSecretCryptoService(() -> key, "viglet-tenant-secret:", resolver);
    }

    /** A resolver that treats only "DEFAULT" as the global-key tenant. */
    private static final VigletTenantKeyResolver MULTI_TENANT = new VigletTenantKeyResolver() {
        @Override
        public String currentTenantId() {
            return null;
        }

        @Override
        public boolean isDefaultTenant(String tenantId) {
            return "DEFAULT".equals(tenantId);
        }
    };

    @Test
    void roundTripWithGlobalKey() {
        var service = service("master-key", VigletTenantKeyResolver.SINGLE_TENANT);
        String encrypted = service.encrypt("secret-value");
        assertThat(encrypted).isNotBlank().isNotEqualTo("secret-value");
        assertThat(service.decrypt(encrypted)).isEqualTo("secret-value");
    }

    @Test
    void encryptReturnsNullForBlankInput() {
        var service = service("k", VigletTenantKeyResolver.SINGLE_TENANT);
        assertThat(service.encrypt("  ")).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void perTenantKeysAreIsolated() {
        var service = service("master-key", MULTI_TENANT);
        String forA = service.encrypt("sk", "tenantA");
        String forB = service.encrypt("sk", "tenantB");

        assertThat(service.decrypt(forA, "tenantA")).isEqualTo("sk");
        assertThat(service.decrypt(forB, "tenantB")).isEqualTo("sk");
        assertThatThrownBy(() -> service.decrypt(forA, "tenantB"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultTenantUsesGlobalKey() {
        var service = service("master-key", MULTI_TENANT);
        String legacy = service.encrypt("token");
        assertThat(service.decrypt(legacy, "DEFAULT")).isEqualTo("token");
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        String encrypted = service("key-A", VigletTenantKeyResolver.SINGLE_TENANT).encrypt("data");
        assertThatThrownBy(() -> service("key-B", VigletTenantKeyResolver.SINGLE_TENANT).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to decrypt provider secret");
    }

    @Test
    void supplierExceptionPropagates() {
        var service = new VigletSecretCryptoService(() -> {
            throw new IllegalStateException("key required");
        });
        assertThatThrownBy(() -> service.encrypt("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key required");
    }
}
