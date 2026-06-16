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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantResolver;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for Turing's tenant resolution (T259). After the T396 / §XIV.9
 * re-home, the gate lives in the shared {@code VigletTenantResolutionFilter}
 * (which {@link TurTenantResolutionFilter} now extends) and the resolution sources
 * live in {@link TurVigletTenantResolver}. These tests exercise both ends through
 * the real chain wired over mocked repositories: the resolver picks the raw token
 * (or auto-provisions / marks trusted), and the inherited gate canonicalizes,
 * checks membership/suspension, binds and clears.
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
    private TurVigletTenantResolver resolver;

    private TurTenantResolutionFilter filter(boolean tenancyEnabled) {
        return filter(tenancyEnabled, false, null);
    }

    private TurTenantResolutionFilter filter(boolean tenancyEnabled, boolean autoProvision) {
        return filter(tenancyEnabled, autoProvision, null);
    }

    private TurTenantResolutionFilter filter(boolean tenancyEnabled, boolean autoProvision,
            String internalToken) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        props.getTenancy().setAutoProvision(autoProvision);
        props.getTenancy().setInternalToken(internalToken);
        tenantContext = new TurTenantContext(props);
        TurVigletTenantStore store = new TurVigletTenantStore(tenantRepository, membershipRepository);
        VigletTenantSlugValidator slugValidator = new VigletTenantSlugValidator(Set.of("turing"));
        VigletTenantMembershipService membershipService =
                new VigletTenantMembershipService(store, slugValidator, null);
        resolver = new TurVigletTenantResolver(membershipService, props);
        TurPlatformAdminService admin = new TurPlatformAdminService(tenantContext);
        return new TurTenantResolutionFilter(tenantContext, store, resolver, admin);
    }

    @AfterEach
    void clearContext() {
        if (tenantContext != null) {
            tenantContext.clear();
        }
        SecurityContextHolder.clearContext();
    }

    private Authentication user(String name) {
        return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
    }

    // --- gate (inherited shared base) -------------------------------------

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
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.ACTIVE);
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

        SecurityContextHolder.getContext().setAuthentication(user("alice"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo("t-acme");
        assertThat(tenantContext.getCurrentTenant()).isNull(); // cleared after the request
    }

    @Test
    void rejectsAuthenticatedUserWithoutActiveMembership() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.ACTIVE);
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(membershipRepository.findByTenant_IdAndUsername("t-acme", "mallory"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(user("mallory"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull(); // chain did NOT proceed
    }

    @Test
    void defaultTenantNeedsNoMembership() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        MockHttpServletRequest req = new MockHttpServletRequest(); // nothing resolves -> DEFAULT
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(user("alice"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(membershipRepository, never()).findByTenant_IdAndUsername(any(), any());
    }

    @Test
    void unresolvableTokenFallsBackToDefault() throws Exception {
        TurTenantResolutionFilter f = filter(true);
        when(tenantRepository.findById("ghost")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "ghost");
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantCapturingChain chain = new TenantCapturingChain(tenantContext);

        f.doFilter(req, res, chain); // unauthenticated -> gate skipped, bound to DEFAULT

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo(TurTenant.DEFAULT_TENANT_ID);
    }

    // --- auto-provision (T333) --------------------------------------------

    @Test
    void autoProvisionsPersonalTenantForAuthenticatedUserWithNoExplicitTenant() throws Exception {
        TurTenantResolutionFilter f = filter(true, true);
        when(membershipRepository.findByUsername("alice")).thenReturn(java.util.List.of());
        when(tenantRepository.findBySlug("alice")).thenReturn(Optional.empty());
        when(tenantRepository.save(any())).thenAnswer(inv -> {
            TurTenant t = inv.getArgument(0);
            t.setId("t-alice");
            return t;
        });
        when(tenantRepository.findById("t-alice")).thenReturn(Optional.of(tenant("t-alice", "alice", TurTenantStatus.ACTIVE)));
        TurTenantMembership owner = new TurTenantMembership();
        owner.setStatus(TurTenantMembershipStatus.ACTIVE);
        when(membershipRepository.findByTenant_IdAndUsername("t-alice", "alice"))
                .thenReturn(Optional.of(owner));

        MockHttpServletRequest req = new MockHttpServletRequest(); // nothing explicit resolves
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantCapturingChain chain = new TenantCapturingChain(tenantContext);

        SecurityContextHolder.getContext().setAuthentication(user("alice"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo("t-alice");
        verify(tenantRepository).save(any());
        verify(membershipRepository).save(any());
    }

    @Test
    void autoProvisionSkippedForPlatformAdmin() throws Exception {
        TurTenantResolutionFilter f = filter(true, true);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        Authentication admin = new UsernamePasswordAuthenticationToken("ops", "n/a",
                java.util.List.of(new SimpleGrantedAuthority(TurPlatformAdminService.ROLE_PLATFORM_ADMIN)));
        SecurityContextHolder.getContext().setAuthentication(admin);
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(membershipRepository, never()).findByUsername(any());
        verify(tenantRepository, never()).save(any());
    }

    // --- trusted internal caller (T334) -----------------------------------

    @Test
    void trustedInternalCallerBypassesMembershipCheck() throws Exception {
        TurTenantResolutionFilter f = filter(true, false, "s3cr3t");
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.ACTIVE);
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(tenantRepository.findById("t-acme")).thenReturn(Optional.of(acme)); // suspension check

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        req.addHeader(TurTenantResolutionFilter.INTERNAL_TOKEN_HEADER, "s3cr3t");
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantCapturingChain chain = new TenantCapturingChain(tenantContext);

        SecurityContextHolder.getContext().setAuthentication(user("svc-dumont"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.tenantSeenDuringChain).isEqualTo("t-acme");
        verify(membershipRepository, never()).findByTenant_IdAndUsername(any(), any());
    }

    @Test
    void wrongInternalTokenStillRequiresMembership() throws Exception {
        TurTenantResolutionFilter f = filter(true, false, "s3cr3t");
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.ACTIVE);
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(membershipRepository.findByTenant_IdAndUsername("t-acme", "svc-dumont"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        req.addHeader(TurTenantResolutionFilter.INTERNAL_TOKEN_HEADER, "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(user("svc-dumont"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void internalTokenIgnoredWhenFeatureDisabled() throws Exception {
        TurTenantResolutionFilter f = filter(true, false, null);
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.ACTIVE);
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(membershipRepository.findByTenant_IdAndUsername("t-acme", "svc-dumont"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        req.addHeader(TurTenantResolutionFilter.INTERNAL_TOKEN_HEADER, "anything");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(user("svc-dumont"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void trustedInternalCallerStillBlockedBySuspendedTenant() throws Exception {
        TurTenantResolutionFilter f = filter(true, false, "s3cr3t");
        TurTenant acme = tenant("t-acme", "acme", TurTenantStatus.SUSPENDED);
        when(tenantRepository.findById("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(acme));
        when(tenantRepository.findById("t-acme")).thenReturn(Optional.of(acme));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "acme");
        req.addHeader(TurTenantResolutionFilter.INTERNAL_TOKEN_HEADER, "s3cr3t");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(user("svc-dumont"));
        f.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    // --- resolution sources (TurVigletTenantResolver) ---------------------

    @Test
    void resolutionPrecedenceSessionOverSubdomainAndHeader() {
        filter(true); // wires the resolver
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setServerName("subdomainco.turing.cloud");
        req.addHeader(TurTenantResolutionFilter.TENANT_HEADER, "headerco");
        req.getSession(true).setAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR, "sess");

        assertThat(resolver.rawTenant(req, null)).isEqualTo("sess");
    }

    @Test
    void subdomainParsingIgnoresReservedHostsApexAndIps() {
        filter(true);
        assertThat(resolver.subdomain("acme.turing.cloud")).isEqualTo("acme");
        assertThat(resolver.subdomain("www.turing.cloud")).isNull();   // reserved
        assertThat(resolver.subdomain("turing.cloud")).isNull();        // apex (2 labels)
        assertThat(resolver.subdomain("localhost")).isNull();           // no dot
        assertThat(resolver.subdomain("127.0.0.1")).isNull();           // IPv4 literal
    }

    @Test
    void resolveReturnsNoneWhenNothingResolvesAndNoAutoProvision() {
        filter(true);
        VigletTenantResolver.Resolution resolution = resolver.resolve(new MockHttpServletRequest(), null);
        assertThat(resolution.rawTenant()).isNull();
        assertThat(resolution.trusted()).isFalse();
    }

    private static TurTenant tenant(String id, String slug, TurTenantStatus status) {
        TurTenant t = new TurTenant();
        t.setId(id);
        t.setSlug(slug);
        t.setStatus(status);
        return t;
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
