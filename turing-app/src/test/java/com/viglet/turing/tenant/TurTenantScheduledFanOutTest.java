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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.model.tenant.TurTenantStatus;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantScheduledFanOut} (T273).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantScheduledFanOutTest {

    @Mock
    private TurTenantRepository tenantRepository;

    private TurTenantScheduledFanOut fanOut(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        return new TurTenantScheduledFanOut(new TurTenantContext(props), tenantRepository);
    }

    private TurTenant tenant(String id, TurTenantStatus status) {
        TurTenant t = new TurTenant();
        t.setId(id);
        t.setStatus(status);
        return t;
    }

    @Test
    void runsOnceWhenTenancyOff() {
        int[] count = {0};
        fanOut(false).forEachActiveTenant(() -> count[0]++);
        assertThat(count[0]).isEqualTo(1);
    }

    @Test
    void runsOncePerActiveTenantSkippingSuspended() {
        when(tenantRepository.findAll()).thenReturn(List.of(
                tenant("a", TurTenantStatus.ACTIVE),
                tenant("b", TurTenantStatus.SUSPENDED),
                tenant("c", TurTenantStatus.ACTIVE)));

        List<String> seen = new ArrayList<>();
        TurTenantScheduledFanOut fanOut = fanOut(true);
        TurTenantContext ctx = new TurTenantContext(enabledProps());
        fanOut.forEachActiveTenant(() -> seen.add(ctx.getCurrentTenant()));

        assertThat(seen).containsExactly("a", "c");
    }

    @Test
    void oneTenantFailureDoesNotAbortTheRest() {
        when(tenantRepository.findAll()).thenReturn(List.of(
                tenant("a", TurTenantStatus.ACTIVE),
                tenant("b", TurTenantStatus.ACTIVE)));

        List<String> completed = new ArrayList<>();
        TurTenantContext ctx = new TurTenantContext(enabledProps());
        fanOut(true).forEachActiveTenant(() -> {
            String current = ctx.getCurrentTenant();
            if ("a".equals(current)) {
                throw new RuntimeException("boom");
            }
            completed.add(current);
        });

        assertThat(completed).containsExactly("b");
    }

    private TurConfigProperties enabledProps() {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(true);
        return props;
    }
}
