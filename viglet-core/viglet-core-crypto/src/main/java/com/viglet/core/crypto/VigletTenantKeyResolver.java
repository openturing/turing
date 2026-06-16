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

/**
 * Strategy that tells {@link VigletSecretCryptoService} which tenant a secret
 * belongs to, and whether that tenant uses the legacy global key.
 *
 * <p>Lifted into {@code viglet-core} (Block Q / T368) so the crypto primitive
 * carries no product knowledge: the notion of a "current tenant" and the
 * identity of the default tenant are supplied by the consumer, never hard-coded
 * here. A single-tenant product uses {@link #SINGLE_TENANT}; a multi-tenant
 * product implements the two methods over its own tenant context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface VigletTenantKeyResolver {

    /**
     * The tenant id whose key should encrypt/decrypt when no explicit tenant is
     * passed, or {@code null} when the product has no notion of a current
     * tenant (single-tenant install).
     */
    String currentTenantId();

    /**
     * Whether {@code tenantId} should use the legacy <em>global</em> key (true)
     * rather than a per-tenant derived key (false). The default tenant always
     * maps to the global key so pre-existing ciphertext stays decryptable.
     */
    boolean isDefaultTenant(String tenantId);

    /**
     * No-tenancy resolver: there is no current tenant and every tenant maps to
     * the global key, so no per-tenant derivation ever happens.
     */
    VigletTenantKeyResolver SINGLE_TENANT = new VigletTenantKeyResolver() {
        @Override
        public String currentTenantId() {
            return null;
        }

        @Override
        public boolean isDefaultTenant(String tenantId) {
            return true;
        }
    };
}
