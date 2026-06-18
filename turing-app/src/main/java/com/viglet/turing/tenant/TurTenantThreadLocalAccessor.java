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

import io.micrometer.context.ThreadLocalAccessor;

/**
 * T274 / §XIV.4.8 — bridges {@link TurTenantContext}'s thread-local to
 * Micrometer's context-propagation, so the current tenant survives the
 * thread-switches Reactor performs on the chat SSE {@code Flux}.
 *
 * <p>Registered with the {@code ContextRegistry} in
 * {@link TurReactiveTenantPropagationConfig}; combined with
 * {@code Hooks.enableAutomaticContextPropagation()} it guarantees the LLM call
 * on a Reactor worker thread resolves the <em>request's</em> tenant rather than
 * {@code DEFAULT} (or, worse, a pooled thread's previous tenant) — the single
 * most likely silent cross-tenant bug.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class TurTenantThreadLocalAccessor implements ThreadLocalAccessor<String> {

    public static final String CONTEXT_KEY = "turing.tenant";

    private final TurTenantContext tenantContext;

    public TurTenantThreadLocalAccessor(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public Object key() {
        return CONTEXT_KEY;
    }

    @Override
    public String getValue() {
        return tenantContext.getCurrentTenant();
    }

    @Override
    public void setValue(String value) {
        tenantContext.setCurrentTenant(value);
    }

    @Override
    public void setValue() {
        tenantContext.clear();
    }
}
