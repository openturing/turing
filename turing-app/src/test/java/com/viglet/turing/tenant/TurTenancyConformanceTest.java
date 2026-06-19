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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.core.tenancy.VigletInfraTenantScope;
import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.core.tenancy.VigletQuotaService;
import com.viglet.core.tenancy.VigletResourceCounter;
import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantMembershipStatus;
import com.viglet.core.tenancy.VigletTenantOwnedInfra;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantResolver;
import com.viglet.core.tenancy.VigletTenantResolutionFilter;
import com.viglet.core.tenancy.VigletTenantRole;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.core.tenancy.VigletTenantStore;
import com.viglet.core.tenancy.VigletTenantTeardownService;
import com.viglet.core.tenancy.VigletTenantTeardownService.VigletTenantTeardownReport;

/**
 * T399 / §XIV.9 — the cross-product <strong>tenancy conformance contract</strong>.
 *
 * <p>Block Q's parity goal is that multi-tenancy is <em>100% identical</em> across
 * Turing, Shio and Dumont because all three run the one shared implementation in
 * {@code viglet-core-tenancy}; the only thing each product supplies is its adapter
 * wiring (its default tenant id, reserved-slug extras, resource keys / plan limits,
 * and resolution sources — the SPI seams of §XIV.9.3). This test proves that
 * promise by driving the <em>shared</em> classes directly and asserting that the
 * same inputs produce the same isolation / visibility / scoping / 402 / 403 /
 * suspend outcomes <strong>regardless of which product's wiring is in play</strong>.
 *
 * <p>It is deliberately product-neutral: it imports only {@code com.viglet.core.tenancy.*}
 * and stands in for each product's SPI implementations with small in-memory fakes
 * ({@link FakeTenantStore}, {@link FakeResourceCounter}, {@link FakeInfra}). Every
 * case is parameterized over three {@link ProductProfile profiles} that mirror the
 * three products' real divergences (Turing {@code DEFAULT} / Shio {@code shio} /
 * Dumont {@code DEFAULT}, with distinct reserved names and resource keys). Because
 * the body is identical for every profile, the same file can be dropped into Shio
 * and Dumont and run against their own SPI beans — the contract is the artifact,
 * the fakes are interchangeable.
 *
 * <p>This is the behaviour-contract lock of the staged T394–T399 effort: the audit
 * (T394) and the lift (T395) established the shared surface; T396–T398 re-homed the
 * three products onto it; this test makes a future divergence (a resolver that
 * defaults differently, a scope check a product forgets) fail loudly here rather
 * than leak across tenants in production.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTenancyConformanceTest {

    // ------------------------------------------------------------------
    // Product profiles — the three SPI wirings under test (the only thing
    // that differs across Turing / Shio / Dumont).
    // ------------------------------------------------------------------

    /**
     * The product-supplied half of the contract: the default (single-tenant)
     * tenant id, the extra reserved slugs, the bounded-resource key and the
     * FREE-plan limit for it. Everything else is shared.
     */
    private record ProductProfile(String name, String defaultTenantId,
            Set<String> extraReserved, String boundedResourceKey, long freeLimit) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Stream<ProductProfile> profiles() {
        return Stream.of(
                new ProductProfile("Turing", "DEFAULT", Set.of("turing"), "agents", 1L),
                new ProductProfile("Shio", "shio", Set.of("shio"), "sites", 1L),
                new ProductProfile("Dumont", "DEFAULT", Set.of("dumont"), "connectors", 1L));
    }

    @AfterEach
    void cleanup() {
        // CURRENT_TENANT / SYSTEM_MODE are static thread-locals shared by every
        // VigletTenantContext instance on this thread — clear them between cases.
        new VigletTenantContext(true, "x").clear();
        SecurityContextHolder.clearContext();
    }

    // ==================================================================
    // A. Tenant context + runAs / system-mode
    // ==================================================================

    @ParameterizedTest(name = "[{0}] context: flag-aware resolve, runAs + system-mode restore")
    @MethodSource("profiles")
    void contextResolvesAndRestores(ProductProfile profile) {
        VigletTenantContext on = new VigletTenantContext(true, profile.defaultTenantId());

        // Unresolved → the configured default; bound → the bound value.
        assertThat(on.resolveCurrentTenant()).isEqualTo(profile.defaultTenantId());
        on.setCurrentTenant("acme");
        assertThat(on.resolveCurrentTenant()).isEqualTo("acme");

        // runAs binds for the duration and restores the prior tenant afterwards.
        String inside = on.runAs("other", () -> {
            assertThat(on.getCurrentTenant()).isEqualTo("other");
            assertThat(on.isSystemMode()).isFalse();
            return on.getCurrentTenant();
        });
        assertThat(inside).isEqualTo("other");
        assertThat(on.getCurrentTenant()).isEqualTo("acme");

        // runAsSystem flips system-mode only for the duration.
        on.runAsSystem(() -> {
            assertThat(on.isSystemMode()).isTrue();
            return null;
        });
        assertThat(on.isSystemMode()).isFalse();
        on.clear();
        assertThat(on.getCurrentTenant()).isNull();

        // Tenancy off: always the default, even after an explicit set.
        VigletTenantContext off = new VigletTenantContext(false, profile.defaultTenantId());
        off.setCurrentTenant("ignored");
        assertThat(off.resolveCurrentTenant()).isEqualTo(profile.defaultTenantId());
    }

    // ==================================================================
    // B. Platform-admin gate
    // ==================================================================

    @ParameterizedTest(name = "[{0}] platform-admin gate: ROLE_PLATFORM_ADMIN required to cross tenants")
    @MethodSource("profiles")
    void platformAdminGate(ProductProfile profile) {
        Stack s = new Stack(profile, true);

        // No authentication → not an admin → both privileged primitives are denied.
        assertThat(s.admin.isPlatformAdmin()).isFalse();
        assertThatThrownBy(() -> s.admin.runForTenant("acme", "support", () -> null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> s.admin.runAsSystem("ops", () -> null))
                .isInstanceOf(SecurityException.class);

        // A plain authenticated user is still not a platform admin.
        authenticateAs("alice", "ROLE_USER");
        assertThat(s.admin.isPlatformAdmin()).isFalse();
        assertThatThrownBy(() -> s.admin.runForTenant("acme", "support", () -> null))
                .isInstanceOf(SecurityException.class);

        // Holding ROLE_PLATFORM_ADMIN unlocks both, binding the impersonated tenant.
        authenticateAs("root", VigletPlatformAdminService.ROLE_PLATFORM_ADMIN);
        assertThat(s.admin.isPlatformAdmin()).isTrue();
        String seen = s.admin.runForTenant("acme", "support", s.context::getCurrentTenant);
        assertThat(seen).isEqualTo("acme");
        Boolean systemSeen = s.admin.runAsSystem("ops", s.context::isSystemMode);
        assertThat(systemSeen).isTrue();
    }

    // ==================================================================
    // C. BYO-infra scope — stamp / visibility / read-only GLOBAL
    // ==================================================================

    @ParameterizedTest(name = "[{0}] infra scope (tenancy ON): stamp, GLOBAL read-only, by-id visibility")
    @MethodSource("profiles")
    void infraScopeWhenTenancyOn(ProductProfile profile) {
        Stack s = new Stack(profile, true);
        s.context.setCurrentTenant("tenantA");

        // stamp-on-create claims a fresh instance for the current tenant...
        assertThat(s.scope.stampOnCreate(new FakeInfra(null)).getTenantId()).isEqualTo("tenantA");
        // ...but never re-stamps one that already has an owner.
        assertThat(s.scope.stampOnCreate(new FakeInfra("tenantB")).getTenantId()).isEqualTo("tenantB");

        // A tenant may write its own, but the GLOBAL (null-tenant) pool is read-only → 403.
        FakeInfra global = new FakeInfra(null);
        FakeInfra own = new FakeInfra("tenantA");
        FakeInfra other = new FakeInfra("tenantB");
        s.scope.assertWritable(own); // no throw
        assertThat(httpStatusOf(() -> s.scope.assertWritable(global)))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // by-id visibility: own + GLOBAL visible, another tenant's is not.
        assertThat(s.scope.isVisibleToTenant(own)).isTrue();
        assertThat(s.scope.isVisibleToTenant(global)).isTrue();
        assertThat(s.scope.isVisibleToTenant(other)).isFalse();

        // A platform admin sees everything and may write GLOBAL; admin create stays GLOBAL.
        authenticateAs("root", VigletPlatformAdminService.ROLE_PLATFORM_ADMIN);
        assertThat(s.scope.isVisibleToTenant(other)).isTrue();
        s.scope.assertWritable(global); // no throw for admin
        assertThat(s.scope.stampOnCreate(new FakeInfra(null)).getTenantId()).isNull();
    }

    @ParameterizedTest(name = "[{0}] infra scope (tenancy OFF): no stamping, GLOBAL editable, all visible")
    @MethodSource("profiles")
    void infraScopeWhenTenancyOff(ProductProfile profile) {
        Stack s = new Stack(profile, false);
        s.context.setCurrentTenant("ignored");

        assertThat(s.scope.stampOnCreate(new FakeInfra(null)).getTenantId()).isNull();
        s.scope.assertWritable(new FakeInfra(null)); // never throws when off
        assertThat(s.scope.isVisibleToTenant(new FakeInfra("anyone"))).isTrue();

        // visibleList returns the full unscoped result when off, the scoped query when on.
        List<String> unscoped = List.of("a", "b");
        assertThat(s.scope.visibleList(() -> unscoped, t -> List.of("scoped"))).isEqualTo(unscoped);

        Stack on = new Stack(profile, true);
        on.context.setCurrentTenant("tenantA");
        assertThat(on.scope.visibleList(() -> unscoped, List::of)).containsExactly("tenantA");
    }

    // ==================================================================
    // D. Quota / plan limits
    // ==================================================================

    @ParameterizedTest(name = "[{0}] quota: FREE bounded → 402 at limit, paid unlimited, default un-throttled")
    @MethodSource("profiles")
    void quotaEnforcement(ProductProfile profile) {
        Stack s = new Stack(profile, true);
        String key = profile.boundedResourceKey();

        // A FREE tenant under its limit passes; at/over the limit → HTTP 402.
        VigletTenantRef free = s.store.seed("acme", "acme", VigletTenantStatus.ACTIVE, "FREE");
        s.context.setCurrentTenant(free.id());
        s.counter.set(free.id(), key, 0);
        s.quota.check(key); // under limit → ok
        s.counter.set(free.id(), key, profile.freeLimit());
        assertThat(httpStatusOf(() -> s.quota.check(key))).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        // A paid plan is unlimited regardless of count.
        VigletTenantRef pro = s.store.seed("big", "big", VigletTenantStatus.ACTIVE, "PRO");
        s.context.setCurrentTenant(pro.id());
        s.counter.set(pro.id(), key, 999);
        s.quota.check(key); // unlimited → ok

        // The default (single-tenant) tenant is never throttled.
        s.context.setCurrentTenant(profile.defaultTenantId());
        s.counter.set(profile.defaultTenantId(), key, 999);
        s.quota.check(key); // no-op

        // Tenancy off → never throttled.
        Stack off = new Stack(profile, false);
        off.counter.set("whatever", key, 999);
        off.quota.check(key); // no-op
    }

    // ==================================================================
    // E. Self-service signup + slug validation
    // ==================================================================

    @ParameterizedTest(name = "[{0}] signup: idempotent, conflict 409, reserved/malformed slug 400, personal tenant")
    @MethodSource("profiles")
    void signupAndSlugRules(ProductProfile profile) {
        Stack s = new Stack(profile, true);

        // First signup creates the tenant + an OWNER membership.
        VigletTenantRef created = s.membership.signup("acme-co", "Acme Co", "alice");
        assertThat(created.slug()).isEqualTo("acme-co");
        assertThat(s.store.isOwnedBy(created.id(), "alice")).isTrue();
        assertThat(s.store.isActiveMember(created.id(), "alice")).isTrue();

        // Re-posting the same (slug, owner) is idempotent — same id back.
        assertThat(s.membership.signup("acme-co", "Acme Co", "alice").id()).isEqualTo(created.id());

        // Same slug, different owner → 409 CONFLICT.
        assertThat(httpStatusOf(() -> s.membership.signup("acme-co", "x", "mallory")))
                .isEqualTo(HttpStatus.CONFLICT);

        // A reserved slug (platform default + this product's extra) → 400.
        assertThat(httpStatusOf(() -> s.membership.signup("admin", "x", "alice")))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        String productReserved = profile.extraReserved().iterator().next();
        assertThat(httpStatusOf(() -> s.membership.signup(productReserved, "x", "alice")))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // A malformed slug (too short / uppercase) → 400.
        assertThat(httpStatusOf(() -> s.membership.signup("A", "x", "alice")))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Personal-tenant resolution is idempotent and owned by the user.
        VigletTenantRef personal = s.membership.resolveOrCreatePersonalTenant("bob@example.com");
        assertThat(personal).isNotNull();
        assertThat(s.store.isOwnedBy(personal.id(), "bob@example.com")).isTrue();
        assertThat(s.membership.resolveOrCreatePersonalTenant("bob@example.com").id())
                .isEqualTo(personal.id());
    }

    // ==================================================================
    // F. Tenant-resolution filter gating
    // ==================================================================

    @ParameterizedTest(name = "[{0}] filter gating: suspended 403, non-member 403, admin/trusted bypass, off no-op")
    @MethodSource("profiles")
    void resolutionFilterGating(ProductProfile profile) throws Exception {
        Stack s = new Stack(profile, true);
        VigletTenantRef acme = s.store.seed("acme", "acme", VigletTenantStatus.ACTIVE, "FREE");
        VigletTenantRef susp = s.store.seed("susp", "susp", VigletTenantStatus.SUSPENDED, "FREE");
        s.store.addMembership(acme.id(), "alice", VigletTenantRole.MEMBER,
                VigletTenantMembershipStatus.ACTIVE);
        s.store.addMembership(susp.id(), "alice", VigletTenantRole.MEMBER,
                VigletTenantMembershipStatus.ACTIVE);

        // An active member resolving their tenant is bound and the chain proceeds.
        authenticateAs("alice", "ROLE_USER");
        BindingChain ok = runFilter(s, "acme", false);
        assertThat(ok.proceeded).isTrue();
        assertThat(ok.boundTenant).isEqualTo("acme");
        assertThat(s.context.getCurrentTenant()).isNull(); // cleared in finally

        // A non-member non-admin is blocked (403) and the chain never runs.
        authenticateAs("bob", "ROLE_USER");
        BindingChain blocked = runFilter(s, "acme", false);
        assertThat(blocked.proceeded).isFalse();
        assertThat(blocked.status).isEqualTo(HttpStatus.FORBIDDEN.value());

        // A suspended tenant blocks even an active member (403).
        authenticateAs("alice", "ROLE_USER");
        BindingChain suspended = runFilter(s, "susp", false);
        assertThat(suspended.proceeded).isFalse();
        assertThat(suspended.status).isEqualTo(HttpStatus.FORBIDDEN.value());

        // A platform admin bypasses the membership check.
        authenticateAs("root", VigletPlatformAdminService.ROLE_PLATFORM_ADMIN);
        BindingChain adminPass = runFilter(s, "acme", false);
        assertThat(adminPass.proceeded).isTrue();
        assertThat(adminPass.boundTenant).isEqualTo("acme");

        // A trusted internal caller vouches for the tenant — membership not checked...
        authenticateAs("service", "ROLE_USER");
        BindingChain trusted = runFilter(s, "acme", true);
        assertThat(trusted.proceeded).isTrue();
        assertThat(trusted.boundTenant).isEqualTo("acme");
        // ...but a suspended tenant still blocks even a trusted call.
        BindingChain trustedSusp = runFilter(s, "susp", true);
        assertThat(trustedSusp.proceeded).isFalse();
        assertThat(trustedSusp.status).isEqualTo(HttpStatus.FORBIDDEN.value());

        // Tenancy off → the filter is a complete no-op (chain runs, nothing bound).
        Stack off = new Stack(profile, false);
        authenticateAs("bob", "ROLE_USER");
        BindingChain noop = runFilter(off, "acme", false);
        assertThat(noop.proceeded).isTrue();
    }

    // ==================================================================
    // G. Teardown / suspend orchestration
    // ==================================================================

    @ParameterizedTest(name = "[{0}] teardown: admin-gated, default-tenant guarded, dry-run vs purge")
    @MethodSource("profiles")
    void teardownOrchestration(ProductProfile profile) {
        Stack s = new Stack(profile, true);
        AtomicBoolean purged = new AtomicBoolean(false);
        VigletTenantTeardownService teardown = new VigletTenantTeardownService(
                s.admin, s.context, s.store, (tenantId, dryRun) -> purged.set(true));

        VigletTenantRef acme = s.store.seed("acme", "acme", VigletTenantStatus.ACTIVE, "FREE");
        s.store.addMembership(acme.id(), "alice", VigletTenantRole.OWNER,
                VigletTenantMembershipStatus.ACTIVE);

        // A non-admin cannot tear down.
        authenticateAs("alice", "ROLE_USER");
        String acmeId = acme.id();
        assertThatThrownBy(() -> teardown.delete(acmeId, false))
                .isInstanceOf(SecurityException.class);

        authenticateAs("root", VigletPlatformAdminService.ROLE_PLATFORM_ADMIN);

        // The default tenant can never be deleted.
        String defaultTenantId = profile.defaultTenantId();
        assertThatThrownBy(() -> teardown.delete(defaultTenantId, false))
                .isInstanceOf(IllegalArgumentException.class);

        // Dry-run plans without purging or deleting rows.
        VigletTenantTeardownReport plan = teardown.delete(acme.id(), true);
        assertThat(plan.dryRun()).isTrue();
        assertThat(plan.tenantExisted()).isTrue();
        assertThat(plan.membershipsRemoved()).isEqualTo(1);
        assertThat(plan.purged()).isFalse();
        assertThat(purged).isFalse();
        assertThat(s.store.findById(acme.id())).isPresent();

        // suspend / activate flip the lifecycle status.
        assertThat(teardown.suspend(acme.id()).status()).isEqualTo(VigletTenantStatus.SUSPENDED);
        assertThat(teardown.activate(acme.id()).status()).isEqualTo(VigletTenantStatus.ACTIVE);

        // A real delete purges the bytes and removes the registry rows.
        VigletTenantTeardownReport done = teardown.delete(acme.id(), false);
        assertThat(done.purged()).isTrue();
        assertThat(done.membershipsRemoved()).isEqualTo(1);
        assertThat(purged).isTrue();
        assertThat(s.store.findById(acme.id())).isEmpty();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** The fully wired shared stack for one product profile + tenancy flag. */
    private static final class Stack {
        final VigletTenantContext context;
        final VigletPlatformAdminService admin;
        final VigletInfraTenantScope scope;
        final FakeTenantStore store;
        final FakeResourceCounter counter;
        final VigletQuotaService quota;
        final VigletTenantMembershipService membership;

        Stack(ProductProfile profile, boolean tenancyEnabled) {
            this.context = new VigletTenantContext(tenancyEnabled, profile.defaultTenantId());
            this.admin = new VigletPlatformAdminService(context);
            this.scope = new VigletInfraTenantScope(context, admin);
            this.store = new FakeTenantStore();
            this.counter = new FakeResourceCounter(profile);
            this.quota = new VigletQuotaService(context, store, counter);
            VigletTenantSlugValidator slug = new VigletTenantSlugValidator(profile.extraReserved());
            this.membership = new VigletTenantMembershipService(store, slug, null);
        }
    }

    /** Drive the shared resolution filter once with a uniform header-based resolver. */
    private BindingChain runFilter(Stack s, String rawTenant, boolean trusted) throws Exception {
        VigletTenantResolver resolver = (request, auth) -> {
            String raw = request.getHeader("X-Conformance-Tenant");
            if (raw == null) {
                return VigletTenantResolver.Resolution.none();
            }
            return trusted ? VigletTenantResolver.Resolution.trusted(raw)
                    : VigletTenantResolver.Resolution.of(raw);
        };
        VigletTenantResolutionFilter filter = new VigletTenantResolutionFilter(
                s.context, s.store, resolver, s.admin);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Conformance-Tenant", rawTenant);
        MockHttpServletResponse response = new MockHttpServletResponse();
        BindingChain chain = new BindingChain(s.context);
        filter.doFilter(request, response, chain);
        chain.status = response.getStatus();
        return chain;
    }

    /** A filter chain that records whether it ran and what tenant was bound at that point. */
    private static final class BindingChain extends MockFilterChain {
        private final VigletTenantContext context;
        boolean proceeded;
        String boundTenant;
        int status;

        BindingChain(VigletTenantContext context) {
            this.context = context;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                jakarta.servlet.ServletResponse response) {
            this.proceeded = true;
            this.boundTenant = context.getCurrentTenant();
        }
    }

    private static void authenticateAs(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = Stream.of(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(username, "n/a", granted);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Run {@code action}, returning the HTTP status of the thrown {@link ResponseStatusException}. */
    private static HttpStatus httpStatusOf(Runnable action) {
        ResponseStatusException ex = catchThrowableOfType(ResponseStatusException.class, action::run);
        assertThat(ex).as("expected a ResponseStatusException").isNotNull();
        return HttpStatus.valueOf(ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // In-memory SPI fakes (stand in for each product's real adapters).
    // ------------------------------------------------------------------

    /** A {@link VigletTenantOwnedInfra} row with a mutable plain-column tenant id. */
    private static final class FakeInfra implements VigletTenantOwnedInfra {
        private String tenantId;

        FakeInfra(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public String getTenantId() {
            return tenantId;
        }

        @Override
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    /** Per-plan limits: FREE bounds the product's resource key; any other plan is unlimited. */
    private static final class FakeResourceCounter implements VigletResourceCounter {
        private final ProductProfile profile;
        private final Map<String, Long> counts = new HashMap<>();

        FakeResourceCounter(ProductProfile profile) {
            this.profile = profile;
        }

        void set(String tenantId, String resourceKey, long value) {
            counts.put(tenantId + "::" + resourceKey, value);
        }

        @Override
        public long count(String tenantId, String resourceKey) {
            return counts.getOrDefault(tenantId + "::" + resourceKey, 0L);
        }

        @Override
        public VigletPlanLimits limitsFor(String plan) {
            if ("FREE".equals(plan)) {
                return VigletPlanLimits.of(profile.boundedResourceKey(), profile.freeLimit());
            }
            return VigletPlanLimits.UNLIMITED;
        }
    }

    /** A minimal in-memory tenant registry + membership store. */
    private static final class FakeTenantStore implements VigletTenantStore {
        private record Membership(String tenantId, String username, VigletTenantRole role,
                VigletTenantMembershipStatus status) {
        }

        private final Map<String, VigletTenantRef> byId = new HashMap<>();
        private final List<Membership> memberships = new ArrayList<>();

        VigletTenantRef seed(String id, String slug, VigletTenantStatus status, String plan) {
            VigletTenantRef ref = new VigletTenantRef(id, slug, slug, status, plan);
            byId.put(id, ref);
            return ref;
        }

        @Override
        public Optional<VigletTenantRef> findById(String tenantId) {
            return Optional.ofNullable(byId.get(tenantId));
        }

        @Override
        public Optional<VigletTenantRef> findBySlug(String slug) {
            return byId.values().stream().filter(t -> t.slug().equals(slug)).findFirst();
        }

        @Override
        public List<VigletTenantRef> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean isActiveMember(String tenantId, String username) {
            return memberships.stream().anyMatch(m -> m.tenantId.equals(tenantId)
                    && m.username.equals(username)
                    && m.status == VigletTenantMembershipStatus.ACTIVE);
        }

        @Override
        public boolean isOwnedBy(String tenantId, String username) {
            return memberships.stream().anyMatch(m -> m.tenantId.equals(tenantId)
                    && m.username.equals(username) && m.role == VigletTenantRole.OWNER);
        }

        @Override
        public List<VigletTenantRef> activeTenantsOf(String username) {
            return memberships.stream()
                    .filter(m -> m.username.equals(username)
                            && m.status == VigletTenantMembershipStatus.ACTIVE)
                    .map(m -> byId.get(m.tenantId))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public Optional<VigletTenantRef> ownedTenantOf(String username) {
            return memberships.stream()
                    .filter(m -> m.username.equals(username) && m.role == VigletTenantRole.OWNER
                            && m.status == VigletTenantMembershipStatus.ACTIVE)
                    .map(m -> byId.get(m.tenantId))
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
        }

        @Override
        public int membershipCount(String tenantId) {
            return (int) memberships.stream().filter(m -> m.tenantId.equals(tenantId)).count();
        }

        @Override
        public VigletTenantRef createTenant(String slug, String name, String plan) {
            String id = UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
            VigletTenantRef ref = new VigletTenantRef(id, slug, name, VigletTenantStatus.ACTIVE, plan);
            byId.put(id, ref);
            return ref;
        }

        @Override
        public void addMembership(String tenantId, String username, VigletTenantRole role,
                VigletTenantMembershipStatus status) {
            memberships.add(new Membership(tenantId, username, role, status));
        }

        @Override
        public void updateStatus(String tenantId, VigletTenantStatus status) {
            VigletTenantRef t = byId.get(tenantId);
            if (t != null) {
                byId.put(tenantId, new VigletTenantRef(t.id(), t.slug(), t.name(), status, t.plan()));
            }
        }

        @Override
        public int deleteTenant(String tenantId) {
            byId.remove(tenantId);
            int removed = (int) memberships.stream().filter(m -> m.tenantId.equals(tenantId)).count();
            memberships.removeIf(m -> m.tenantId.equals(tenantId));
            return removed;
        }
    }
}
