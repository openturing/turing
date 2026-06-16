/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.tenancy;

import java.util.function.Supplier;

/**
 * Request-scoped holder of the <em>current tenant</em> that a discriminator
 * {@code CurrentTenantIdentifierResolver} reads to stamp inserts and filter
 * reads.
 *
 * <p>Backed by a {@link ThreadLocal} so a value set by a resolution filter on
 * the request thread is visible to every JPA call on that thread. Pooled threads
 * must never leak a tenant, so callers <strong>must</strong> {@link #clear()} in
 * a {@code finally} block — the {@link #runAs} / {@link #runAsSystem} helpers do
 * this for you and are the preferred entry points for scheduled jobs and JMS
 * listeners.
 *
 * <p>{@link #resolveCurrentTenant()} short-circuits to the configured
 * {@code defaultTenantId} whenever tenancy is off, so nothing downstream needs to
 * be flag-aware — single-tenant installs always see one constant tenant.
 *
 * <p>Lifted from Turing's {@code TurTenantContext} (T258) into {@code viglet-core}
 * (T370 / Block Q). The framework-level behaviour is identical; the
 * product-specific bits (the {@code turing.tenancy.enabled} flag source and the
 * {@code DEFAULT} tenant constant) are passed in via the constructor so the class
 * stays neutral.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletTenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM_MODE = new ThreadLocal<>();

    private final boolean tenancyEnabled;
    private final String defaultTenantId;

    /**
     * @param tenancyEnabled  whether multi-tenancy is switched on for this deployment
     * @param defaultTenantId the immutable tenant resolved when tenancy is off, or
     *                        when on but nothing is bound to the thread
     */
    public VigletTenantContext(boolean tenancyEnabled, String defaultTenantId) {
        this.tenancyEnabled = tenancyEnabled;
        this.defaultTenantId = defaultTenantId;
    }

    /** Whether multi-tenancy is switched on for this deployment. */
    public boolean isTenancyEnabled() {
        return tenancyEnabled;
    }

    /** The configured default tenant id (used when tenancy is off / unresolved). */
    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    /** Bind {@code tenantId} to the current thread (raw — no flag short-circuit). */
    public void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /** The raw bound tenant id, or {@code null} if none was set on this thread. */
    public String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * The tenant id to use for persistence. When tenancy is off, always the
     * configured {@code defaultTenantId}; when on, the bound tenant or, as a
     * safety net for unauthenticated/unresolved paths, the default.
     */
    public String resolveCurrentTenant() {
        if (!tenancyEnabled) {
            return defaultTenantId;
        }
        String current = CURRENT_TENANT.get();
        return current != null ? current : defaultTenantId;
    }

    /**
     * Privileged mode: a platform-admin / ops context that is permitted to read
     * across tenants. The bypass is a marker only — callers that honour it (e.g.
     * a cross-tenant query path) check {@link #isSystemMode()}.
     */
    public boolean isSystemMode() {
        return Boolean.TRUE.equals(SYSTEM_MODE.get());
    }

    /** Remove all tenant state from the current thread (call in {@code finally}). */
    public void clear() {
        CURRENT_TENANT.remove();
        SYSTEM_MODE.remove();
    }

    /** Run {@code action} bound to {@code tenantId}, restoring prior state afterwards. */
    public void runAs(String tenantId, Runnable action) {
        runAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /** Run {@code action} bound to {@code tenantId}, restoring prior state afterwards. */
    public <T> T runAs(String tenantId, Supplier<T> action) {
        String previous = CURRENT_TENANT.get();
        boolean previousSystem = isSystemMode();
        try {
            CURRENT_TENANT.set(tenantId);
            SYSTEM_MODE.remove();
            return action.get();
        } finally {
            restore(previous, previousSystem);
        }
    }

    /** Run {@code action} in privileged {@link #isSystemMode() system mode}. */
    public void runAsSystem(Runnable action) {
        runAsSystem(() -> {
            action.run();
            return null;
        });
    }

    /** Run {@code action} in privileged {@link #isSystemMode() system mode}. */
    public <T> T runAsSystem(Supplier<T> action) {
        String previous = CURRENT_TENANT.get();
        boolean previousSystem = isSystemMode();
        try {
            SYSTEM_MODE.set(Boolean.TRUE);
            return action.get();
        } finally {
            restore(previous, previousSystem);
        }
    }

    private void restore(String previousTenant, boolean previousSystem) {
        if (previousTenant != null) {
            CURRENT_TENANT.set(previousTenant);
        } else {
            CURRENT_TENANT.remove();
        }
        if (previousSystem) {
            SYSTEM_MODE.set(Boolean.TRUE);
        } else {
            SYSTEM_MODE.remove();
        }
    }
}
