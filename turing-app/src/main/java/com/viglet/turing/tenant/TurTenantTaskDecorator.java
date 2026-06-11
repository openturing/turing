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

import org.springframework.core.task.TaskDecorator;

/**
 * T274 / §XIV.4.8 — propagates the current tenant across {@code @Async} /
 * {@code TaskExecutor} hops by capturing it on the submitting thread and
 * restoring it on the worker thread (clearing afterwards so a pooled worker
 * never leaks a tenant into the next task).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public class TurTenantTaskDecorator implements TaskDecorator {

    private final TurTenantContext tenantContext;

    public TurTenantTaskDecorator(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        String capturedTenant = tenantContext.getCurrentTenant();
        return () -> {
            if (capturedTenant != null) {
                tenantContext.setCurrentTenant(capturedTenant);
            }
            try {
                runnable.run();
            } finally {
                tenantContext.clear();
            }
        };
    }
}
