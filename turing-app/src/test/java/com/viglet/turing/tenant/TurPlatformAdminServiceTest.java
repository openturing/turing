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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurPlatformAdminService} (T266).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurPlatformAdminServiceTest {

    private final TurTenantContext tenantContext = newContext();
    private final TurPlatformAdminService service = new TurPlatformAdminService(tenantContext);

    private TurTenantContext newContext() {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(true);
        return new TurTenantContext(props);
    }

    private void authenticateAs(String name, String... authorities) {
        var granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(name, "n/a", granted));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    @Test
    void runForTenantImpersonatesWhenPlatformAdmin() {
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);

        String boundDuring = service.runForTenant("t-9", "support ticket #42",
                tenantContext::getCurrentTenant);

        assertThat(boundDuring).isEqualTo("t-9");
        assertThat(tenantContext.getCurrentTenant()).isNull(); // restored after
    }

    @Test
    void runAsSystemSetsSystemModeWhenPlatformAdmin() {
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);

        boolean systemDuring = service.runAsSystem("registry sweep", tenantContext::isSystemMode);

        assertThat(systemDuring).isTrue();
        assertThat(tenantContext.isSystemMode()).isFalse();
    }

    @Test
    void runAsSystemRequiresPlatformAdmin() {
        authenticateAs("regular", "ROLE_ADMIN");

        assertThatThrownBy(() -> service.runAsSystem("oops", () -> null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void runForTenantRequiresPlatformAdmin() {
        authenticateAs("regular", "ROLE_ADMIN");

        assertThatThrownBy(() -> service.runForTenant("t-1", "oops", () -> null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void isPlatformAdminReflectsAuthorities() {
        assertThat(service.isPlatformAdmin()).isFalse();
        authenticateAs("ops", TurPlatformAdminService.ROLE_PLATFORM_ADMIN);
        assertThat(service.isPlatformAdmin()).isTrue();
    }
}
