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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.core.tenancy.VigletTenantMembershipService;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantResolver;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * T396 / §XIV.9 — the {@link VigletTenantResolver} SPI carrying <em>only</em>
 * Turing's resolution sources; the shared {@code VigletTenantResolutionFilter}
 * owns the gating (suspended → 403, non-member → 403, platform-admin bypass,
 * always-clear) that used to be written twice across products.
 *
 * <p>Resolution precedence, highest first (unchanged from T259):
 * <ol>
 *   <li>JWT/OIDC {@code tenant} claim,</li>
 *   <li>session attribute {@link TurTenantResolutionFilter#TENANT_SESSION_ATTR}
 *       (set by the T265 switch),</li>
 *   <li>request subdomain ({@code <slug>.turing.cloud}),</li>
 *   <li>{@link TurTenantResolutionFilter#TENANT_HEADER} header.</li>
 * </ol>
 * The raw token (id-or-slug) is returned uncanonicalized — the shared filter
 * canonicalizes it via the {@link com.viglet.core.tenancy.VigletTenantStore}.
 *
 * <p>T334 — a server-to-server caller presenting {@link
 * TurTenantResolutionFilter#INTERNAL_TOKEN_HEADER} matching
 * {@code turing.tenancy.internal-token} marks the resolution
 * {@link VigletTenantResolver.Resolution#trusted() trusted}, so the shared gate
 * skips the per-user membership check (a suspended tenant still blocks).
 *
 * <p>T333 — when no explicit tenant resolves and auto-provision is on, an
 * authenticated non-platform-admin principal lands in their own personal tenant,
 * created on first request via the shared
 * {@link VigletTenantMembershipService#resolveOrCreatePersonalTenant(String)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurVigletTenantResolver implements VigletTenantResolver {

    private final VigletTenantMembershipService membershipService;
    private final boolean autoProvisionEnabled;
    private final String internalToken;

    public TurVigletTenantResolver(VigletTenantMembershipService membershipService,
            TurConfigProperties configProperties) {
        this.membershipService = membershipService;
        this.autoProvisionEnabled = configProperties.getTenancy().isAutoProvision();
        this.internalToken = configProperties.getTenancy().getInternalToken();
    }

    @Override
    public Resolution resolve(HttpServletRequest request, Authentication authentication) {
        String raw = rawTenant(request, authentication);
        if (StringUtils.hasText(raw)) {
            // A trusted internal caller vouches for the tenant it stamps.
            return isTrustedInternalCaller(request) ? Resolution.trusted(raw) : Resolution.of(raw);
        }
        // T333 — no explicit tenant. With auto-provision on, an authenticated
        // (non-platform-admin) principal lands in their own personal tenant.
        if (autoProvisionEnabled
                && isAuthenticated(authentication)
                && !isPlatformAdmin(authentication)) {
            VigletTenantRef personal = membershipService
                    .resolveOrCreatePersonalTenant(authentication.getName());
            if (personal != null) {
                return Resolution.of(personal.id());
            }
        }
        return Resolution.none();
    }

    /** The raw id-or-slug token Turing's ordered sources select, or {@code null}. */
    String rawTenant(HttpServletRequest request, Authentication authentication) {
        return firstNonBlank(
                tenantFromClaim(authentication),
                tenantFromSession(request),
                subdomain(request.getServerName()),
                request.getHeader(TurTenantResolutionFilter.TENANT_HEADER));
    }

    private String tenantFromClaim(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2AuthenticatedPrincipal oauth2) {
            Object claim = oauth2.getAttribute(TurTenantResolutionFilter.TENANT_CLAIM);
            return claim != null ? claim.toString() : null;
        }
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString(TurTenantResolutionFilter.TENANT_CLAIM);
        }
        return null;
    }

    private String tenantFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR);
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
        return TurTenantResolutionFilter.RESERVED_SUBDOMAINS.contains(candidate) ? null : candidate;
    }

    /**
     * T334 — true when the feature is configured ({@code turing.tenancy.internal-token})
     * and the request presents a matching {@link TurTenantResolutionFilter#INTERNAL_TOKEN_HEADER}.
     * The comparison is constant-time to avoid leaking the token through timing.
     */
    private boolean isTrustedInternalCaller(HttpServletRequest request) {
        if (!StringUtils.hasText(internalToken)) {
            return false;
        }
        String provided = request.getHeader(TurTenantResolutionFilter.INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> VigletPlatformAdminService.ROLE_PLATFORM_ADMIN.equals(a.getAuthority()));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
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
