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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T280 / §XIV.7.1 — the adversarial cross-tenant isolation gate, exercised
 * through the <em>real wired beans</em> in a full Spring context with tenancy
 * enabled.
 *
 * <p>Companion to the surface-specific ITs:
 * <ul>
 *   <li>JPA {@code @TenantId} reads — {@code TurSNSiteTenantIsolationIT};</li>
 *   <li>BYO-infra {@code current ∪ NULL} visibility — {@code TurTenantInfraVisibilityIT};</li>
 *   <li>cache-key partitioning — {@code TurTenantCacheKeyGeneratorTest};</li>
 *   <li>core/storage/Lucene naming — their respective helper unit tests.</li>
 * </ul>
 * This class adds the <strong>secret-crypto</strong> surface end-to-end: a
 * secret encrypted in tenant A's context must be undecryptable in tenant B's,
 * and the legacy/DEFAULT key path must keep working.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCrossTenantIsolationIT extends AbstractTuringSpringIT {

    @DynamicPropertySource
    static void enableTenancy(DynamicPropertyRegistry registry) {
        registry.add("turing.tenancy.enabled", () -> "true");
        // A real crypto key so PBKDF2 derivation runs (not the dev fallback).
        registry.add("turing.ai.crypto.key", () -> "cross-tenant-isolation-master-key");
    }

    @Autowired
    private TurSecretCryptoService secretCryptoService;
    @Autowired
    private TurTenantContext tenantContext;

    @Test
    void secretEncryptedForOneTenantIsUndecryptableByAnother() {
        String cipherForA = tenantContext.runAs("tenantA",
                () -> secretCryptoService.encrypt("sk-super-secret"));

        // Same tenant round-trips.
        String backForA = tenantContext.runAs("tenantA",
                () -> secretCryptoService.decrypt(cipherForA));
        assertThat(backForA).isEqualTo("sk-super-secret");

        // Tenant B's derived key cannot decrypt tenant A's ciphertext.
        assertThatThrownBy(() -> tenantContext.runAs("tenantB",
                () -> secretCryptoService.decrypt(cipherForA)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultTenantUsesLegacyKeyRoundTrip() {
        // No binding → DEFAULT → legacy global key; must round-trip.
        String cipher = secretCryptoService.encrypt("global-token");
        assertThat(secretCryptoService.decrypt(cipher)).isEqualTo("global-token");
    }
}
