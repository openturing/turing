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

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T267 / §XIV.4.1 — installs {@link TurTenantCacheKeyGenerator} as the
 * project-wide default cache key generator so every {@code @Cacheable} without
 * an explicit {@code key} is automatically partitioned by tenant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class TurCacheTenancyConfig implements CachingConfigurer {

    private final TurTenantContext tenantContext;

    public TurCacheTenancyConfig(TurTenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new TurTenantCacheKeyGenerator(tenantContext);
    }
}
