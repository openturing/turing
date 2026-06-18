/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.testsupport.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.turing.genai.rag.rerank.TurRagRerankCache;

/**
 * Standalone cache context for {@code TurRagRerankCacheTest} (T341).
 *
 * <p>Lives under {@code com.viglet.testsupport.*}, <b>outside</b> the
 * {@code com.viglet.turing} {@code @SpringBootApplication} component-scan base,
 * so its fixed-name {@link ConcurrentMapCacheManager} is never picked up by an
 * unrelated {@code @SpringBootTest}. A nested {@code @Configuration} (or even a
 * {@code @TestConfiguration}) under {@code com.viglet.turing} <em>did</em> leak
 * here — it replaced the application's auto-creating {@code CacheManager} with
 * one that only knew {@code turRagRerankScore}, breaking every other
 * {@code @Cacheable} name (e.g. {@code turConfigVarfindById}) and failing the
 * whole context on startup. Placing it outside the scan base is the project's
 * prescribed remedy (see CLAUDE.md → "@TestConfiguration must live outside the
 * scan").
 *
 * <p>It is loaded explicitly via
 * {@code new AnnotationConfigApplicationContext(TurRagRerankCacheTestConfig.class)},
 * so it does not need to be component-scannable at all.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
@EnableCaching
public class TurRagRerankCacheTestConfig {

    @Bean
    ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager("turRagRerankScore");
    }

    @Bean
    TurRagRerankCache rerankCache() {
        return new TurRagRerankCache();
    }
}
