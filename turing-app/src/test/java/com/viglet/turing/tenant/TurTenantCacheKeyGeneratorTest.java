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
 * Unit tests for {@link TurTenantCacheKeyGenerator} (T267): the cache key is
 * partitioned by the current tenant, so the same logical key in two tenants
 * never collides.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantCacheKeyGeneratorTest {

    private final TurConfigProperties props = enabledProps();
    private final TurTenantContext context = new TurTenantContext(props);
    private final TurTenantCacheKeyGenerator generator = new TurTenantCacheKeyGenerator(context);

    private TurConfigProperties enabledProps() {
        TurConfigProperties p = new TurConfigProperties();
        p.getTenancy().setEnabled(true);
        return p;
    }

    @AfterEach
    void clear() {
        context.clear();
    }

    @Test
    void sameKeyInDifferentTenantsProducesDistinctCacheKeys() {
        Object keyA = context.runAs("tenantA", () -> generator.generate(this, method(), "intranet"));
        Object keyB = context.runAs("tenantB", () -> generator.generate(this, method(), "intranet"));

        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    void sameKeyInSameTenantIsStable() {
        Object first = context.runAs("tenantA", () -> generator.generate(this, method(), "intranet"));
        Object second = context.runAs("tenantA", () -> generator.generate(this, method(), "intranet"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void keyIsPrefixedWithDefaultTenantWhenNoneBound() {
        Object key = generator.generate(this, method(), "x");

        assertThat(key.toString()).contains(TurTenant.DEFAULT_TENANT_ID);
    }

    private java.lang.reflect.Method method() {
        return TurTenantCacheKeyGeneratorTest.class.getMethods()[0];
    }
}
