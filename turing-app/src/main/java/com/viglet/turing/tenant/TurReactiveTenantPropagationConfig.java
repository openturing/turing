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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Hooks;

/**
 * T274 / §XIV.4.8 — wires tenant propagation across reactive and async
 * boundaries.
 *
 * <ul>
 *   <li>Registers {@link TurTenantThreadLocalAccessor} with the global
 *       {@code ContextRegistry} and enables Reactor automatic context
 *       propagation, so the tenant rides the chat SSE {@code Flux} across
 *       thread switches.</li>
 *   <li>Exposes a {@link TaskDecorator} bean so {@code @Async} executors restore
 *       the tenant on their worker threads.</li>
 * </ul>
 *
 * Both are inert when tenancy is off (the propagated value is always
 * {@code DEFAULT}/null).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class TurReactiveTenantPropagationConfig {

    private final TurTenantContext tenantContext;

    public TurReactiveTenantPropagationConfig(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @PostConstruct
    public void enableContextPropagation() {
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(new TurTenantThreadLocalAccessor(tenantContext));
        Hooks.enableAutomaticContextPropagation();
    }

    @Bean
    public TaskDecorator turTenantTaskDecorator() {
        return new TurTenantTaskDecorator(tenantContext);
    }
}
