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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantContext} (T258) — the flag-aware,
 * thread-local current-tenant holder.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantContextTest {

    private TurTenantContext context(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        return new TurTenantContext(props);
    }

    @AfterEach
    void cleanThreadLocal() {
        // The ThreadLocal is static; never let one test leak into the next.
        context(true).clear();
    }

    @Test
    void resolvesDefaultWhenTenancyDisabledEvenIfAValueIsBound() {
        TurTenantContext ctx = context(false);
        ctx.setCurrentTenant("acme");

        assertThat(ctx.resolveCurrentTenant()).isEqualTo(TurTenant.DEFAULT_TENANT_ID);
    }

    @Test
    void resolvesBoundTenantWhenEnabled() {
        TurTenantContext ctx = context(true);
        ctx.setCurrentTenant("acme");

        assertThat(ctx.getCurrentTenant()).isEqualTo("acme");
        assertThat(ctx.resolveCurrentTenant()).isEqualTo("acme");
    }

    @Test
    void resolvesDefaultWhenEnabledButNothingBound() {
        TurTenantContext ctx = context(true);

        assertThat(ctx.getCurrentTenant()).isNull();
        assertThat(ctx.resolveCurrentTenant()).isEqualTo(TurTenant.DEFAULT_TENANT_ID);
    }

    @Test
    void clearRemovesBoundTenantAndSystemMode() {
        TurTenantContext ctx = context(true);
        ctx.setCurrentTenant("acme");

        ctx.clear();

        assertThat(ctx.getCurrentTenant()).isNull();
        assertThat(ctx.isSystemMode()).isFalse();
    }

    @Test
    void runAsBindsThenRestoresPriorTenant() {
        TurTenantContext ctx = context(true);
        ctx.setCurrentTenant("outer");

        String seen = ctx.runAs("inner", ctx::getCurrentTenant);

        assertThat(seen).isEqualTo("inner");
        assertThat(ctx.getCurrentTenant()).isEqualTo("outer");
    }

    @Test
    void runAsRestoresEmptyStateWhenNothingWasBound() {
        TurTenantContext ctx = context(true);

        ctx.runAs("inner", () -> assertThat(ctx.getCurrentTenant()).isEqualTo("inner"));

        assertThat(ctx.getCurrentTenant()).isNull();
    }

    @Test
    void runAsSystemSetsSystemModeForTheDurationThenRestores() {
        TurTenantContext ctx = context(true);
        assertThat(ctx.isSystemMode()).isFalse();

        boolean insideSystemMode = ctx.runAsSystem(ctx::isSystemMode);

        assertThat(insideSystemMode).isTrue();
        assertThat(ctx.isSystemMode()).isFalse();
    }

    @Test
    void runAsClearsSystemModeWhileBoundToTenant() {
        TurTenantContext ctx = context(true);

        ctx.runAsSystem(() -> {
            assertThat(ctx.isSystemMode()).isTrue();
            ctx.runAs("acme", () -> assertThat(ctx.isSystemMode()).isFalse());
            // restored to system mode after the nested runAs
            assertThat(ctx.isSystemMode()).isTrue();
        });
    }

    @Test
    void exposesTheConfiguredFlag() {
        assertThat(context(true).isTenancyEnabled()).isTrue();
        assertThat(context(false).isTenancyEnabled()).isFalse();
    }
}
