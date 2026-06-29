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

import com.viglet.turing.genai.flow.TurChatFlowGraphCache;

/**
 * Standalone cache context for {@code TurChatFlowGraphCacheTest} (T487).
 *
 * <p>Lives under {@code com.viglet.testsupport.*}, <b>outside</b> the
 * {@code com.viglet.turing} component-scan base, so its fixed-name
 * {@link ConcurrentMapCacheManager} cannot leak into an unrelated
 * {@code @SpringBootTest} (see CLAUDE.md → "@TestConfiguration must live outside
 * the scan"). Loaded explicitly via {@code AnnotationConfigApplicationContext}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
@EnableCaching
public class TurChatFlowGraphCacheTestConfig {

    @Bean
    ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(TurChatFlowGraphCache.CACHE_NAME);
    }

    @Bean
    TurChatFlowGraphCache graphCache() {
        return new TurChatFlowGraphCache();
    }
}
