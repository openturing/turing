/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.tenant.TurTenantResolutionFilter;
import com.viglet.turing.tenant.TurTenantService;

/**
 * Unit tests for {@link TurTenantAPI} (T265).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantAPITest {

    @Mock
    private TurTenantService turTenantService;
    @Mock
    private TurTenantRepository turTenantRepository;

    private final Principal alice = () -> "alice";

    private TurTenantAPI api() {
        return new TurTenantAPI(turTenantService, turTenantRepository);
    }

    @Test
    void myTenantsMapsActiveMemberships() {
        TurTenant t = new TurTenant();
        t.setId("t-1");
        t.setSlug("acme");
        t.setName("Acme");
        when(turTenantService.tenantsOf("alice")).thenReturn(List.of(t));

        List<TurTenantResponse> result = api().myTenants(alice);

        assertThat(result).singleElement()
                .satisfies(r -> assertThat(r.slug()).isEqualTo("acme"));
    }

    @Test
    void switchSetsSessionAttributeForActiveMember() {
        TurTenant t = new TurTenant();
        t.setId("t-1");
        t.setSlug("acme");
        when(turTenantService.normalizeSlug("acme")).thenReturn("acme");
        when(turTenantRepository.findBySlug("acme")).thenReturn(Optional.of(t));
        when(turTenantService.isActiveMember("t-1", "alice")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        TurTenantResponse response = api().switchTenant("acme", alice, request);

        assertThat(response.id()).isEqualTo("t-1");
        assertThat(request.getSession(false)
                .getAttribute(TurTenantResolutionFilter.TENANT_SESSION_ATTR)).isEqualTo("t-1");
    }

    @Test
    void switchRejectsNonMemberWith403() {
        TurTenant t = new TurTenant();
        t.setId("t-1");
        when(turTenantService.normalizeSlug("acme")).thenReturn("acme");
        when(turTenantRepository.findBySlug("acme")).thenReturn(Optional.of(t));
        when(turTenantService.isActiveMember("t-1", "alice")).thenReturn(false);

        var api = api();
        var request = new MockHttpServletRequest();
        assertThatThrownBy(() -> api.switchTenant("acme", alice, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
