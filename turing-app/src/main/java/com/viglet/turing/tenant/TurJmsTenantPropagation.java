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

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.tenant.TurTenant;

/**
 * T272 / §XIV.4.6 — propagates the current tenant across the Artemis JMS hop.
 *
 * <p>A JMS send happens on the request thread (tenant bound) but the listener
 * runs on a pooled consumer thread where the {@code ThreadLocal} is gone. The
 * sender stamps the tenant as a message header ({@link #HEADER}); the listener
 * restores it into {@link TurTenantContext} for the duration of message
 * processing and clears it afterwards (a pooled consumer thread must never leak
 * a tenant into the next message).
 *
 * <p>When tenancy is off — or the tenant is {@code DEFAULT} — no header is sent
 * and the listener resolves {@code DEFAULT}, so existing single-tenant queues
 * are unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurJmsTenantPropagation {

    /** Message header carrying the originating tenant id. */
    public static final String HEADER = "turingTenantId";

    private final TurTenantContext tenantContext;

    public TurJmsTenantPropagation(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /** Headers to attach on send — the tenant id, or empty when off/DEFAULT. */
    public Map<String, Object> headers() {
        if (!tenantContext.isTenancyEnabled()) {
            return Map.of();
        }
        String tenant = tenantContext.resolveCurrentTenant();
        if (!StringUtils.hasText(tenant) || TurTenant.DEFAULT_TENANT_ID.equals(tenant)) {
            return Map.of();
        }
        return Map.of(HEADER, tenant);
    }

    /** Run {@code action} with the tenant from a received message header restored. */
    public void runForHeader(String tenantId, Runnable action) {
        if (StringUtils.hasText(tenantId)) {
            tenantContext.runAs(tenantId, action);
        } else {
            // No header → DEFAULT tenant; still clear in finally so a pooled
            // consumer thread cannot leak a tenant from a previous message.
            try {
                action.run();
            } finally {
                tenantContext.clear();
            }
        }
    }

    /** Variant returning a value. */
    public <T> T runForHeader(String tenantId, Supplier<T> action) {
        if (StringUtils.hasText(tenantId)) {
            return tenantContext.runAs(tenantId, action);
        }
        try {
            return action.get();
        } finally {
            tenantContext.clear();
        }
    }
}
