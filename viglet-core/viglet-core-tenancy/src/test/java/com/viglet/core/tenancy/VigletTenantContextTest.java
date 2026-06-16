/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.core.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VigletTenantContext} — the flag-aware, thread-local
 * current-tenant holder.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletTenantContextTest {

    private static final String DEFAULT = "DEFAULT";

    private VigletTenantContext context(boolean tenancyEnabled) {
        return new VigletTenantContext(tenancyEnabled, DEFAULT);
    }

    @AfterEach
    void cleanThreadLocal() {
        // The ThreadLocal is static; never let one test leak into the next.
        context(true).clear();
    }

    @Test
    void resolvesDefaultWhenTenancyDisabledEvenIfAValueIsBound() {
        VigletTenantContext ctx = context(false);
        ctx.setCurrentTenant("acme");

        assertThat(ctx.resolveCurrentTenant()).isEqualTo(DEFAULT);
    }

    @Test
    void resolvesBoundTenantWhenEnabled() {
        VigletTenantContext ctx = context(true);
        ctx.setCurrentTenant("acme");

        assertThat(ctx.getCurrentTenant()).isEqualTo("acme");
        assertThat(ctx.resolveCurrentTenant()).isEqualTo("acme");
    }

    @Test
    void resolvesDefaultWhenEnabledButNothingBound() {
        VigletTenantContext ctx = context(true);

        assertThat(ctx.getCurrentTenant()).isNull();
        assertThat(ctx.resolveCurrentTenant()).isEqualTo(DEFAULT);
    }

    @Test
    void clearRemovesBoundTenantAndSystemMode() {
        VigletTenantContext ctx = context(true);
        ctx.setCurrentTenant("acme");

        ctx.clear();

        assertThat(ctx.getCurrentTenant()).isNull();
        assertThat(ctx.isSystemMode()).isFalse();
    }

    @Test
    void runAsBindsThenRestoresPriorTenant() {
        VigletTenantContext ctx = context(true);
        ctx.setCurrentTenant("outer");

        String seen = ctx.runAs("inner", ctx::getCurrentTenant);

        assertThat(seen).isEqualTo("inner");
        assertThat(ctx.getCurrentTenant()).isEqualTo("outer");
    }

    @Test
    void runAsRestoresEmptyStateWhenNothingWasBound() {
        VigletTenantContext ctx = context(true);

        ctx.runAs("inner", () -> assertThat(ctx.getCurrentTenant()).isEqualTo("inner"));

        assertThat(ctx.getCurrentTenant()).isNull();
    }

    @Test
    void runAsSystemSetsSystemModeForTheDurationThenRestores() {
        VigletTenantContext ctx = context(true);
        assertThat(ctx.isSystemMode()).isFalse();

        boolean insideSystemMode = ctx.runAsSystem(ctx::isSystemMode);

        assertThat(insideSystemMode).isTrue();
        assertThat(ctx.isSystemMode()).isFalse();
    }

    @Test
    void runAsClearsSystemModeWhileBoundToTenant() {
        VigletTenantContext ctx = context(true);

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
