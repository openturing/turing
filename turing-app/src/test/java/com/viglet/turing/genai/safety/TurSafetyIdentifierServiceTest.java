/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import com.viglet.turing.tenant.TurTenantContext;

/**
 * T181 / §X.14.a — unit coverage for the {@code safety_identifier} derivation:
 * deterministic per (sub, tenant, salt); empty for anonymous / disabled.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSafetyIdentifierServiceTest {

    @Mock
    private TurTenantContext tenantContext;

    private Authentication authenticated(String sub) {
        return new UsernamePasswordAuthenticationToken(sub, "n/a",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    @Test
    void emitsDeterministicHexHashForAuthenticatedUser() {
        when(tenantContext.resolveCurrentTenant()).thenReturn("DEFAULT");
        TurSafetyIdentifierService service = new TurSafetyIdentifierService(tenantContext, true, "salt");

        Optional<String> first = service.safetyIdentifierFor(authenticated("kc-sub-123"));
        Optional<String> second = service.safetyIdentifierFor(authenticated("kc-sub-123"));

        assertThat(first).isPresent();
        // 64 hex chars = SHA-256 and never the raw subject.
        assertThat(first.get()).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("kc-sub-123");
        assertThat(second).contains(first.get());
    }

    @Test
    void differentSubjectsAndTenantsHashDifferently() {
        when(tenantContext.resolveCurrentTenant()).thenReturn("tenant-a");
        TurSafetyIdentifierService service = new TurSafetyIdentifierService(tenantContext, true, "salt");
        String a = service.safetyIdentifierFor(authenticated("alice")).orElseThrow();
        String b = service.safetyIdentifierFor(authenticated("bob")).orElseThrow();
        assertThat(a).isNotEqualTo(b);

        when(tenantContext.resolveCurrentTenant()).thenReturn("tenant-b");
        String aOtherTenant = service.safetyIdentifierFor(authenticated("alice")).orElseThrow();
        assertThat(aOtherTenant).isNotEqualTo(a);
    }

    @Test
    void emptyForAnonymousVisitor() {
        TurSafetyIdentifierService service = new TurSafetyIdentifierService(tenantContext, true, "salt");
        Authentication anon = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(service.safetyIdentifierFor(anon)).isEmpty();
        assertThat(service.safetyIdentifierFor(null)).isEmpty();
    }

    @Test
    void emptyWhenDisabledEvenForAuthenticatedUser() {
        lenient().when(tenantContext.resolveCurrentTenant()).thenReturn("DEFAULT");
        TurSafetyIdentifierService service = new TurSafetyIdentifierService(tenantContext, false, "salt");
        assertThat(service.safetyIdentifierFor(authenticated("kc-sub-123"))).isEmpty();
    }
}
