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
package com.viglet.core.tenancy;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The <strong>only sanctioned</strong> way to operate across tenant boundaries,
 * reserved for {@link #ROLE_PLATFORM_ADMIN} (support / ops).
 *
 * <p>Two privileged primitives, both <em>audited</em> on every call (who + why +
 * target):
 * <ul>
 *   <li>{@link #runForTenant} — impersonate a specific tenant (the safe path a
 *       cross-tenant console uses to read a single tenant's owned data);</li>
 *   <li>{@link #runAsSystem} — enter {@link VigletTenantContext#isSystemMode()
 *       system mode} for registry-level work that is not itself tenant-scoped.</li>
 * </ul>
 * Both require the caller to hold {@code ROLE_PLATFORM_ADMIN}; otherwise a
 * {@link SecurityException} is thrown before any context switch.
 *
 * <p>Lifted from Turing's {@code TurPlatformAdminService} (T266) into
 * {@code viglet-core} (T370 / Block Q). The authority check reads the
 * Spring Security context, so it is product-agnostic — granting the authority
 * (from a Keycloak realm role, a DB role, etc.) stays each product's concern.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletPlatformAdminService {

    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(VigletPlatformAdminService.class);

    private final VigletTenantContext tenantContext;

    public VigletPlatformAdminService(VigletTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /** Run {@code action} impersonating {@code tenantId}; audited. */
    public <T> T runForTenant(String tenantId, String reason, Supplier<T> action) {
        requirePlatformAdmin();
        log.warn("PLATFORM-ADMIN cross-tenant access by '{}' → tenant '{}' (reason: {})",
                currentPrincipal(), tenantId, reason);
        return tenantContext.runAs(tenantId, action);
    }

    /** Run {@code action} in privileged system mode (registry-level ops); audited. */
    public <T> T runAsSystem(String reason, Supplier<T> action) {
        requirePlatformAdmin();
        log.warn("PLATFORM-ADMIN system-mode access by '{}' (reason: {})",
                currentPrincipal(), reason);
        return tenantContext.runAsSystem(action);
    }

    /** Whether the current authentication holds the platform-admin role. */
    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ROLE_PLATFORM_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private void requirePlatformAdmin() {
        if (!isPlatformAdmin()) {
            throw new SecurityException("Cross-tenant access requires " + ROLE_PLATFORM_ADMIN);
        }
    }

    private String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
