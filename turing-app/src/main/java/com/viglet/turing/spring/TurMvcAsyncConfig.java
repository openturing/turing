/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.viglet.turing.tenant.TurTenantContext;
import com.viglet.turing.tenant.TurTenantTaskDecorator;

/**
 * Provides the {@link AsyncTaskExecutor} used by Spring MVC for asynchronous
 * request handling (SSE streaming chat, {@code Flux}/{@code SseEmitter}
 * controllers, etc.).
 *
 * <p>Without this, Spring MVC falls back to a {@code SimpleAsyncTaskExecutor}
 * that spawns an unbounded number of unpooled threads per async request and
 * logs the well-known warning:
 *
 * <pre>
 * Performing asynchronous handling through the default Spring MVC
 * SimpleAsyncTaskExecutor. This executor is not suitable for production use
 * under load. Please, configure an AsyncTaskExecutor through the WebMvc config.
 * </pre>
 *
 * <p>The pool is decorated with {@link TurTenantTaskDecorator} so the current
 * tenant is propagated onto the worker thread that resumes the async dispatch
 * (and cleared afterwards), keeping multi-tenant SSE responses tenant-scoped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
public class TurMvcAsyncConfig {

    public static final String EXECUTOR_BEAN = "turMvcAsyncTaskExecutor";

    @Bean(EXECUTOR_BEAN)
    AsyncTaskExecutor turMvcAsyncTaskExecutor(TurTenantContext tenantContext) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setTaskDecorator(new TurTenantTaskDecorator(tenantContext));
        executor.initialize();
        return executor;
    }
}
