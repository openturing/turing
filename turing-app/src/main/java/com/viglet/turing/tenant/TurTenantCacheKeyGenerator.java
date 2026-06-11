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

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

/**
 * T267 / §XIV.4.1 — the project-wide default cache {@link KeyGenerator}, which
 * prefixes every generated key with the current tenant.
 *
 * <p>Without this, a cache keyed by a non-unique value (e.g. a site
 * {@code findByName("intranet")}) would let tenant B read the entry tenant A
 * populated — the single most likely cache cross-tenant leak. Prefixing the key
 * with {@link TurTenantContext#resolveCurrentTenant()} partitions every cache by
 * tenant. When tenancy is off the prefix is the constant {@code DEFAULT}, so
 * single-tenant installs see one partition and behave exactly as before.
 *
 * <p>Applies to every {@code @Cacheable} that does not declare an explicit
 * {@code key} SpEL (those bypass the key generator entirely — they are
 * overwhelmingly id-keyed, where the global-unique UUID is already safe).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class TurTenantCacheKeyGenerator implements KeyGenerator {

    private final TurTenantContext tenantContext;

    public TurTenantCacheKeyGenerator(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Object base = SimpleKeyGenerator.generateKey(params);
        return List.of(tenantContext.resolveCurrentTenant(), base);
    }
}
