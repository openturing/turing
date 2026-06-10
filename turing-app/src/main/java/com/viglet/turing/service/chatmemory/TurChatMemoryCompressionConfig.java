/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * T309 / §IX.3.f — bounded executor for off-the-hot-path memory compression.
 *
 * <p>The {@link TurChatMemoryCompressionWorker} dispatches summary
 * regeneration onto this pool so the user's chat turn never blocks on the
 * summary LLM call. The pool is deliberately small and bounded: compression is
 * a best-effort, eventual background job (the triggering turn proceeds
 * uncompressed), so a saturated queue should shed work rather than pile up or
 * spill back onto request threads.
 *
 * <p>{@link ThreadPoolExecutor.AbortPolicy} is used so a rejected submission
 * surfaces as a {@code TaskRejectedException} the caller catches and treats as
 * "skip this regeneration" — the per-conversation interval guard will try
 * again on a later turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class TurChatMemoryCompressionConfig {

    public static final String EXECUTOR_BEAN = "turChatMemoryCompressionExecutor";

    @Bean(EXECUTOR_BEAN)
    ThreadPoolTaskExecutor turChatMemoryCompressionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-mem-compress-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Don't hold shutdown for in-flight summaries — they're best-effort.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
