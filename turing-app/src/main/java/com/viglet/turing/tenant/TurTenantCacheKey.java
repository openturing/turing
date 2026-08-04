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

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantContext;

/**
 * T895 / §LXVIII.4 — prefixes a hand-written cache key with the current tenant.
 *
 * <p>
 * {@link TurTenantCacheKeyGenerator} partitions every {@code @Cacheable} by
 * tenant, but only those that let it generate the key: an explicit {@code key}
 * SpEL bypasses the generator entirely. That exemption was taken on the grounds
 * that explicitly-keyed caches are id-keyed and a UUID is already globally
 * unique — true for five of the eight, and false for the three keyed on the
 * <strong>SN site name</strong>, which is unique only within a tenant. Two
 * tenants that each own a site called {@code intranet} shared cache entries:
 * cached search answers, the site snapshot (config and entity graph) and the
 * field config an LLM tool reads.
 * </p>
 *
 * <p>
 * Rather than three differently-shaped SpEL fixes, every such cache wraps its key
 * in {@link #scoped(String)}. The wrapper is the thing the conformance lint in
 * {@code TurCacheAnnotationConventionsTest} looks for, so a new explicitly-keyed
 * cache either scopes itself or has to be listed there with a reason — which is
 * what stops the next one from landing silently.
 * </p>
 *
 * <p>
 * When tenancy is off, {@code resolveCurrentTenant()} is the constant
 * {@code DEFAULT}, so a single-tenant install gets one partition and behaves as
 * before. The separator is {@code ::} — absent from tenant ids (slug/UUID) and
 * from the key shapes in use, so no pair of (tenant, key) can spell another.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component("turTenantCacheKey")
public class TurTenantCacheKey {

    /** Separator between the tenant and the caller's key. */
    public static final String SEPARATOR = "::";

    private final VigletTenantContext tenantContext;

    /**
     * Takes the shared {@link VigletTenantContext} rather than Turing's
     * {@link TurTenantContext} subclass: the only thing needed here is
     * {@code resolveCurrentTenant()}, which is the kernel's. Injection still resolves
     * to {@code TurTenantContext} — it is the only bean of the type.
     */
    public TurTenantCacheKey(VigletTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /**
     * {@code <currentTenant>::<key>}. A {@code null} key is scoped as the empty
     * string rather than the literal {@code "null"}, so a caller whose key
     * collapses is still confined to its own tenant.
     */
    public String scoped(String key) {
        return tenantContext.resolveCurrentTenant() + SEPARATOR + (key == null ? "" : key);
    }
}
