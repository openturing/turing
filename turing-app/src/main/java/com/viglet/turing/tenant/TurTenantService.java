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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.model.tenant.TurTenantRole;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;

/**
 * T264 / §XIV.3.2 — self-service tenant lifecycle: create a tenant with an OWNER
 * membership, list a user's tenants (T265), and validate slugs.
 *
 * <p>Signup is idempotent: re-posting the same {@code (slug, owner)} returns the
 * existing tenant rather than failing, so a retried request is safe. A slug
 * already owned by <em>someone else</em> is a 409 conflict; a malformed or
 * reserved slug is a 400.
 *
 * <p>Keycloak user provisioning via the Admin API is intentionally NOT on this
 * hot path (§XIV.1 decision 2) — most signups come from an already-authenticated
 * principal; provisioning a brand-new identity is a separate, optional concern.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTenantService {

    /** {@code <label>}: 2–63 chars, lowercase alphanumeric and inner hyphens. */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$");

    /** Slugs that collide with platform hosts/paths and must never be a tenant. */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "app", "api", "admin", "console", "turing", "localhost",
            "signup", "login", "logout", "oauth2", "static", "assets", "default",
            "system", "platform", "support", "mail", "smtp", "ftp", "root");

    private final TurTenantRepository tenantRepository;
    private final TurTenantMembershipRepository membershipRepository;

    public TurTenantService(TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Create (or idempotently return) the tenant identified by {@code slug},
     * owned by {@code ownerUsername}.
     */
    @Transactional
    public TurTenant signup(String slug, String name, String ownerUsername) {
        String normalizedSlug = normalizeSlug(slug);
        if (!StringUtils.hasText(ownerUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An owner username is required");
        }

        TurTenant existing = tenantRepository.findBySlug(normalizedSlug).orElse(null);
        if (existing != null) {
            // Idempotent: same owner already provisioned → return it; else conflict.
            boolean ownedByCaller = membershipRepository
                    .findByTenant_IdAndUsername(existing.getId(), ownerUsername)
                    .filter(m -> m.getRole() == TurTenantRole.OWNER)
                    .isPresent();
            if (ownedByCaller) {
                return existing;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tenant slug '" + normalizedSlug + "' is already taken");
        }

        TurTenant tenant = new TurTenant();
        tenant.setSlug(normalizedSlug);
        tenant.setName(StringUtils.hasText(name) ? name.trim() : normalizedSlug);
        TurTenant saved = tenantRepository.save(tenant);

        TurTenantMembership ownerMembership = new TurTenantMembership();
        ownerMembership.setTenant(saved);
        ownerMembership.setUsername(ownerUsername);
        ownerMembership.setRole(TurTenantRole.OWNER);
        ownerMembership.setStatus(TurTenantMembershipStatus.ACTIVE);
        membershipRepository.save(ownerMembership);

        return saved;
    }

    /**
     * T333 / §XIV.8 — resolve (or, on first call, auto-provision) the
     * <em>personal</em> tenant of {@code username}: the tenant they OWN, created
     * on demand so each user lands in their own isolated environment. Idempotent
     * and race-safe — a concurrent first request reuses the tenant the winner
     * created rather than minting a second one.
     *
     * @return the user's personal tenant, or {@code null} when {@code username}
     *         is blank (the caller then falls back to {@code DEFAULT}).
     */
    @Transactional
    public TurTenant resolveOrCreatePersonalTenant(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        // Fast path: the user already owns a personal tenant.
        Optional<TurTenant> owned = membershipRepository.findByUsername(username).stream()
                .filter(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE
                        && m.getRole() == TurTenantRole.OWNER)
                .map(TurTenantMembership::getTenant)
                .findFirst();
        if (owned.isPresent()) {
            return owned.get();
        }
        // Provisioning path: find a slug we can own, then create the tenant.
        String base = basePersonalSlug(username);
        String candidate = base;
        int suffix = 1;
        while (true) {
            Optional<TurTenant> existing = tenantRepository.findBySlug(candidate);
            if (existing.isEmpty()) {
                break; // free slug — provision it below
            }
            // Slug taken: reuse if it's already ours (handles a provisioning race),
            // otherwise try the next suffix.
            boolean mine = membershipRepository
                    .findByTenant_IdAndUsername(existing.get().getId(), username)
                    .filter(m -> m.getRole() == TurTenantRole.OWNER)
                    .isPresent();
            if (mine) {
                return existing.get();
            }
            suffix++;
            candidate = base + "-" + suffix;
        }
        return signup(candidate, username, username);
    }

    /**
     * Derive a valid, non-reserved slug base from an arbitrary username (which
     * may be an email or an OIDC {@code sub} UUID). Uniqueness is handled by the
     * caller; this only guarantees the slug passes {@link #SLUG_PATTERN} and is
     * not reserved.
     */
    String basePersonalSlug(String username) {
        String base = username.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-") // any run of non-alphanumerics → one hyphen
                .replaceAll("^-+|-+$", "");    // trim leading/trailing hyphens
        if (base.length() < 2) {
            base = "user-" + (base.isEmpty() ? "x" : base);
        }
        if (base.length() > 56) { // leave headroom for a "-<n>" uniqueness suffix
            base = base.substring(0, 56).replaceAll("-+$", "");
        }
        if (RESERVED_SLUGS.contains(base)) {
            base = "u-" + base;
        }
        return base;
    }

    /** Every tenant {@code username} is an active member of (T265 "my tenants"). */
    @Transactional(readOnly = true)
    public List<TurTenant> tenantsOf(String username) {
        return membershipRepository.findByUsername(username).stream()
                .filter(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE)
                .map(TurTenantMembership::getTenant)
                .toList();
    }

    /** Whether {@code username} has an active membership in {@code tenantId}. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(String tenantId, String username) {
        return membershipRepository.findByTenant_IdAndUsername(tenantId, username)
                .map(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE)
                .orElse(false);
    }

    /** All tenants (platform-admin console, T279). The registry has no @TenantId. */
    @Transactional(readOnly = true)
    public List<TurTenant> findAll() {
        return tenantRepository.findAll();
    }

    /** Set a tenant's lifecycle status (T279 suspend / reactivate). */
    @Transactional
    public TurTenant setStatus(String tenantId, TurTenantStatus status) {
        TurTenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
        tenant.setStatus(status);
        return tenantRepository.save(tenant);
    }

    /** Validate + normalize a slug, rejecting malformed or reserved values (400). */
    public String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tenant slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid slug: use 2–63 lowercase letters, digits or hyphens");
        }
        if (RESERVED_SLUGS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug '" + normalized + "' is reserved");
        }
        return normalized;
    }
}
