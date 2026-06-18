/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Explicit, deterministic {@link CacheManager} wiring.
 *
 * <p><b>Why this exists.</b> The app annotates many repository methods with
 * {@code @Cacheable("turXxxfindById")} and relies on the cache manager creating
 * those caches on demand — no cache name is pre-declared anywhere. Spring Boot's
 * cache auto-configuration in this Spring Boot 4 + Hazelcast 5 setup did not
 * reliably yield a manager that creates caches lazily, so the very first
 * cacheable call on startup — {@code TurConfigVarRepository.findById(FIRST_TIME)}
 * from {@code TurOnStartup} — failed with
 * {@code IllegalArgumentException: Cannot find cache named 'turConfigVarfindById'}
 * and the whole context failed to load.
 *
 * <p>Declaring the {@code CacheManager} explicitly (and {@code @Primary}) removes
 * that ambiguity — Boot's cache auto-config backs off on
 * {@code @ConditionalOnMissingBean(CacheManager)} — and also makes the app
 * resilient to a stray test {@code @Configuration} that leaks a fixed-name
 * manager into the scan (a {@code @Primary} bean wins resolution):
 * <ul>
 *   <li>When a {@link HazelcastInstance} is present (production / clustered),
 *       use the Hazelcast-backed manager — its {@code getCache} creates the
 *       backing {@code IMap} on demand, so any {@code turXxx*} name resolves.</li>
 *   <li>Otherwise (dev / tests with embedded Hazelcast disabled), fall back to a
 *       {@link ConcurrentMapCacheManager} left in its <em>dynamic</em> default
 *       (no fixed name list) so it likewise auto-creates caches on first use.</li>
 * </ul>
 *
 * <p>This matches the project's documented intent (Hazelcast-backed cache when
 * the cluster lib is on the classpath, in-process map otherwise) while making
 * lazy cache creation guaranteed rather than incidental.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Configuration
public class TurCacheConfig {

    @Bean
    @Primary
    CacheManager cacheManager(ObjectProvider<HazelcastInstance> hazelcastInstanceProvider) {
        HazelcastInstance hazelcastInstance = hazelcastInstanceProvider.getIfAvailable();
        if (hazelcastInstance != null) {
            log.info("[Cache] Using Hazelcast-backed CacheManager (caches created on demand)");
            return new HazelcastCacheManager(hazelcastInstance);
        }
        // Dynamic by default — never pass cache names here, or unlisted
        // turXxx* caches would resolve to null and fail @Cacheable lookups.
        log.info("[Cache] No HazelcastInstance present — using dynamic ConcurrentMapCacheManager");
        return new ConcurrentMapCacheManager();
    }
}
