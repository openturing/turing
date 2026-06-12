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

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantMembership;
import com.viglet.turing.persistence.model.tenant.TurTenantMembershipStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantMembershipRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * T259 / §XIV.2.3 — resolves the <em>current tenant</em> for each request and
 * binds it to {@link TurTenantContext} for the duration of the request thread.
 *
 * <p>Registered <strong>after</strong> the authentication filters (so the
 * principal is known) in {@code TurSecurityConfigProduction}. Resolution
 * priority, highest first:
 * <ol>
 *   <li>JWT/OIDC {@code tenant} claim,</li>
 *   <li>session attribute {@link #TENANT_SESSION_ATTR} (set by the T265 switch),</li>
 *   <li>request subdomain ({@code <slug>.turing.cloud}),</li>
 *   <li>{@link #TENANT_HEADER} header.</li>
 * </ol>
 * A raw token may be a tenant id or a slug; it is resolved to the canonical id
 * via the {@link TurTenantRepository}. For an authenticated principal, a
 * non-DEFAULT tenant requires an {@link TurTenantMembershipStatus#ACTIVE active}
 * {@link TurTenantMembership} or the request is rejected with HTTP 403.
 *
 * <p>The whole filter is a no-op when {@code turing.tenancy.enabled=false}, so a
 * single-tenant install never pays a DB lookup and behaves exactly as before.
 * The bound tenant is always {@link TurTenantContext#clear() cleared} in a
 * {@code finally} block — a pooled request thread must never leak a tenant into
 * the next request.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurTenantResolutionFilter extends OncePerRequestFilter {

    /** Header carrying an explicit tenant id/slug (lowest precedence). */
    public static final String TENANT_HEADER = "X-Turing-Tenant";

    /** HTTP session attribute the T265 switch API writes the active tenant id into. */
    public static final String TENANT_SESSION_ATTR = "TURING_TENANT";

    /** Token claim that selects the active membership for OIDC/JWT principals. */
    public static final String TENANT_CLAIM = "tenant";

    /**
     * Host labels that are never a tenant slug (platform hosts). A subdomain
     * matching one of these is ignored so {@code app.turing.cloud} doesn't
     * resolve to a phantom "app" tenant.
     */
    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
            "www", "app", "api", "admin", "console", "localhost", "turing");

    private final TurTenantContext tenantContext;
    private final TurTenantRepository tenantRepository;
    private final TurTenantMembershipRepository membershipRepository;
    private final TurTenantService tenantService;
    private final boolean tenancyEnabled;
    private final boolean autoProvisionEnabled;

    public TurTenantResolutionFilter(TurTenantContext tenantContext,
            TurTenantRepository tenantRepository,
            TurTenantMembershipRepository membershipRepository,
            TurTenantService tenantService,
            TurConfigProperties configProperties) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.tenantService = tenantService;
        this.tenancyEnabled = configProperties.getTenancy().isEnabled();
        this.autoProvisionEnabled = configProperties.getTenancy().isAutoProvision();
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!tenancyEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String tenantId = resolveTenantId(request, authentication);

            if (isAuthenticated(authentication)
                    && !TurTenant.DEFAULT_TENANT_ID.equals(tenantId)
                    && !isPlatformAdmin(authentication)) {
                // T281 / §XIV.7.2 — a suspended tenant blocks its members' access.
                if (isSuspended(tenantId)) {
                    log.warn("Tenant '{}' is suspended — blocking access for '{}'",
                            tenantId, authentication.getName());
                    reject(response);
                    return;
                }
                if (!hasActiveMembership(tenantId, authentication.getName())) {
                    log.warn("Principal '{}' has no active membership in tenant '{}'",
                            authentication.getName(), tenantId);
                    reject(response);
                    return;
                }
            }

            tenantContext.setCurrentTenant(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    /**
     * Apply the resolution precedence and canonicalize the raw token to a
     * tenant id, falling back to {@link TurTenant#DEFAULT_TENANT_ID} when no
     * tenant can be resolved.
     */
    String resolveTenantId(HttpServletRequest request, Authentication authentication) {
        String raw = firstNonBlank(
                tenantFromClaim(authentication),
                tenantFromSession(request),
                subdomain(request.getServerName()),
                request.getHeader(TENANT_HEADER));
        if (StringUtils.hasText(raw)) {
            return canonicalize(raw).orElse(TurTenant.DEFAULT_TENANT_ID);
        }
        // T333 / §XIV.8 — no explicit tenant. With auto-provision on, an
        // authenticated (non-platform-admin) principal lands in their own
        // personal tenant, created on first request. This is the
        // "one environment per user" posture; without it the principal stays on
        // DEFAULT (legacy invite-based model).
        if (autoProvisionEnabled
                && isAuthenticated(authentication)
                && !isPlatformAdmin(authentication)) {
            TurTenant personal = tenantService.resolveOrCreatePersonalTenant(authentication.getName());
            if (personal != null) {
                return personal.getId();
            }
        }
        return TurTenant.DEFAULT_TENANT_ID;
    }

    private String tenantFromClaim(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2) {
            Object claim = oauth2.getAttribute(TENANT_CLAIM);
            return claim != null ? claim.toString() : null;
        }
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString(TENANT_CLAIM);
        }
        return null;
    }

    private String tenantFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(TENANT_SESSION_ATTR);
        return attr instanceof String s ? s : null;
    }

    /** Extract the leftmost label of a multi-label host, ignoring platform hosts and IPs. */
    String subdomain(String serverName) {
        if (!StringUtils.hasText(serverName) || serverName.indexOf('.') < 0) {
            return null;
        }
        // An IPv4 literal has only numeric labels — never a slug.
        if (serverName.replace(".", "").chars().allMatch(Character::isDigit)) {
            return null;
        }
        String[] labels = serverName.split("\\.");
        if (labels.length < 3) {
            return null; // need <sub>.<domain>.<tld>; bare apex/2-label is not a tenant
        }
        String candidate = labels[0].toLowerCase();
        return RESERVED_SUBDOMAINS.contains(candidate) ? null : candidate;
    }

    /** Resolve a raw id-or-slug token to the canonical tenant id. */
    private Optional<String> canonicalize(String raw) {
        if (tenantRepository.findById(raw).isPresent()) {
            return Optional.of(raw);
        }
        return tenantRepository.findBySlug(raw).map(TurTenant::getId);
    }

    private boolean hasActiveMembership(String tenantId, String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return membershipRepository.findByTenant_IdAndUsername(tenantId, username)
                .map(m -> m.getStatus() == TurTenantMembershipStatus.ACTIVE)
                .orElse(false);
    }

    private boolean isSuspended(String tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> t.getStatus() == com.viglet.turing.persistence.model.tenant.TurTenantStatus.SUSPENDED)
                .orElse(false);
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> TurPlatformAdminService.ROLE_PLATFORM_ADMIN.equals(a.getAuthority()));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Forbidden: no active membership in tenant\"}");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
