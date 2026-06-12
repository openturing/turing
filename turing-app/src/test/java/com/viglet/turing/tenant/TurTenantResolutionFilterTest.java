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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantResolutionFilter} (T259).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantResolutionFilterTest {

    @Mock
    private TurTenantRepository tenantRepository;
    @Mock
    private TurTenantMembershipRepository membershipRepository;

    private TurTenantContext tenantContext;

    private TurTenantResolutionFilter filter(boolean tenancyEnabled) {
        return filter(tenancyEnabled, false);
    }

    private TurTenantResolutionFilter filter(boolean tenancyEnabled, boolean autoProvision) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        props.getTenancy().setAutoProvision(autoProvision);
        tenantContext = new TurTenantContext(props);
        TurTenantService tenantService = new TurTenantService(tenantRepository, membershipRepository);
        return new TurTenantResolutionFilter(tenantContext, tenantRepository, membershipRepository,
                tenantService, props);
    }

    @AfterEach
    void clearContext() {
        if (tenantContext != null) {
            tenantContext.clear();
        }
    }

    private Authentication user(String name) {
        return new UsernamePasswordAuthenticationToken(name, "n/a",
                java.util.List.of());
    }

    @Test
    void noOpWhenTenancyDisabled() throws Exception {
        TurTenantResolutionFilter f = filter(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        f.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isSameAs(req); // chain proceeded
        verify(tenantRepository, never()).findById(any());
    }

    @Test
    void headerTenantResolvedBySlugThenValidatedAndBound() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        TurTenant acme = new TurTenant();
        acme.setId("t-acme");
        acme.setSlug("acme");
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        TurTenantMembership m = new TurTenantMembership();
        m.setStatus(TurTenantMembershipStatus.ACTIVE);
        when(membershipRepository.findByTenant_IdAndUsername("t-acme", "alice"))
                .thenReturn(Optional.of(m));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantCapturingChain chain = new TenantCapturingChain(tenantContext);

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(user("alice"));
        try {
            f.doFilter(req, res, chain);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo("t-acme");
        // cleared after the request
        assertThat(tenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void rejectsAuthenticatedUserWithoutActiveMembership() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        TurTenant acme = new TurTenant();
        acme.setId("t-acme");
        acme.setSlug("acme");
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(membershipRepository.findByTenant_IdAndUsername("t-acme", "mallory"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(user("mallory"));
        try {
            f.doFilter(req, res, chain);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull(); // chain did NOT proceed
    }

    @Test
    void defaultTenantNeedsNoMembership() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        MockHttpServletRequest req = new MockHttpServletRequest(); // nothing resolves -> DEFAULT
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(user("alice"));
        try {
            f.doFilter(req, res, chain);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(res.getStatus()).isEqualTo(200);
        verify(membershipRepository, never()).findByTenant_IdAndUsername(any(), any());
    }

    @Test
    void autoProvisionsPersonalTenantForAuthenticatedUserWithNoExplicitTenant() throws Exception {
        TurTenantResolutionFilter f = filter(true, true);
        // No personal tenant yet → provisioning path runs.
        when(membershipRepository.findByUsername("alice")).thenReturn(java.util.List.of());
        when(tenantRepository.findBySlug("alice")).thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(inv -> {
            TurTenant t = inv.getArgument(0);
            t.setId("t-alice");
            return t;
        });
        // Post-provision membership check in doFilterInternal sees the new OWNER membership.
        TurTenant personal = new TurTenant();
        personal.setId("t-alice");
        when(tenantRepository.findById("t-alice")).thenReturn(Optional.of(personal));
        TurTenantMembership owner = new TurTenantMembership();
        owner.setStatus(TurTenantMembershipStatus.ACTIVE);
        when(membershipRepository.findByTenant_IdAndUsername("t-alice", "alice"))
                .thenReturn(Optional.of(owner));

        MockHttpServletRequest req = new MockHttpServletRequest(); // nothing explicit resolves
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantCapturingChain chain = new TenantCapturingChain(tenantContext);

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(user("alice"));
        try {
            f.doFilter(req, res, chain);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo("t-alice");
        // a tenant + an owner membership were persisted
        verify(tenantRepository).save(any());
        verify(membershipRepository).save(any());
    }

    @Test
    void autoProvisionSkippedForPlatformAdmin() throws Exception {
        TurTenantResolutionFilter f = filter(true, true);
        MockHttpServletRequest req = new MockHttpServletRequest(); // nothing explicit resolves
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Authentication admin = new UsernamePasswordAuthenticationToken("ops", "n/a",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        TurPlatformAdminService.ROLE_PLATFORM_ADMIN)));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(admin);
        try {
            f.doFilter(req, res, chain);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        assertThat(res.getStatus()).isEqualTo(200);
        // platform admin stays on DEFAULT — no personal tenant minted
        verify(membershipRepository, never()).findByUsername(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void resolutionPrecedenceSessionOverSubdomainAndHeader() {
        TurTenantResolutionFilter f = filter(true);
        TurTenant fromSession = new TurTenant();
        fromSession.setId("sess");
        lenient().when(tenantRepository.findById("sess")).thenReturn(Optional.of(fromSession));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServerName("subdomainco.turing.cloud");
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "headerco");
        req.getSession(true).setAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR, "sess");

        String resolved = f.resolveTenantId(req, null);

        assertThat(resolved).isEqualTo("sess");
    }

    @Test
    void subdomainParsingIgnoresReservedHostsApexAndIps() {
        TurTenantResolutionFilter f = filter(true);

        assertThat(f.subdomain("acme.turing.cloud")).isEqualTo("acme");
        assertThat(f.subdomain("www.turing.cloud")).isNull();   // reserved
        assertThat(f.subdomain("turing.cloud")).isNull();        // apex (2 labels)
        assertThat(f.subdomain("localhost")).isNull();           // no dot
        assertThat(f.subdomain("127.0.0.1")).isNull();           // IPv4 literal
    }

    @Test
    void unresolvableTokenFallsBackToDefault() {
        TurTenantResolutionFilter f = filter(true);
        when(tenantRepository.findById("ghost")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "ghost");

        assertThat(f.resolveTenantId(req, null)).isEqualTo(TurTenant.DEFAULT_TENANT_ID);
    }

    /** Captures the bound tenant id at the moment the chain runs (i.e. inside the request). */
    private static final class TenantCapturingChain extends MockFilterChain {
        private final TurTenantContext ctx;
        private String tenantSeenDuringChain;

        TenantCapturingChain(TurTenantContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                jakarta.servlet.ServletResponse response) {
            this.tenantSeenDuringChain = ctx.getCurrentTenant();
        }
    }
}
