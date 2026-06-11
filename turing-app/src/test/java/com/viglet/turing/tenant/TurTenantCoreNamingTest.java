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

import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantCoreNaming} (T268).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantCoreNamingTest {

    private TurTenantContext context;

    private TurTenantCoreNaming naming(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        context = new TurTenantContext(props);
        return new TurTenantCoreNaming(context);
    }

    @AfterEach
    void clear() {
        if (context != null) {
            context.clear();
        }
    }

    @Test
    void noPrefixWhenTenancyDisabled() {
        TurTenantCoreNaming naming = naming(false);
        assertThat(context.runAs("acme0000-1111", () -> naming.scoped("intranet_en")))
                .isEqualTo("intranet_en");
    }

    @Test
    void noPrefixForDefaultTenant() {
        TurTenantCoreNaming naming = naming(true);
        // nothing bound -> DEFAULT -> no prefix
        assertThat(naming.scoped("intranet_en")).isEqualTo("intranet_en");
    }

    @Test
    void prefixesWithShortTenantIdWhenEnabledAndBound() {
        TurTenantCoreNaming naming = naming(true);
        String scoped = context.runAs("abcdef12-3456-7890", () -> naming.scoped("intranet_en"));
        assertThat(scoped).isEqualTo("tabcdef12_intranet_en");
    }

    @Test
    void differentTenantsProduceDifferentCoreNames() {
        TurTenantCoreNaming naming = naming(true);
        String a = context.runAs("tenant-aaaa", () -> naming.scoped("intranet_en"));
        String b = context.runAs("tenant-bbbb", () -> naming.scoped("intranet_en"));
        assertThat(a).isNotEqualTo(b);
    }
}
