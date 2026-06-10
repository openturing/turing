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

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService.CompressionJob;

import lombok.extern.slf4j.Slf4j;

/**
 * T309 / §IX.3.f — async dispatch boundary for memory compression.
 *
 * <p>A thin {@code @Async} proxy that runs the summary regeneration on the
 * {@link TurChatMemoryCompressionConfig#EXECUTOR_BEAN bounded compression pool}
 * so the user's chat turn never blocks on the summary LLM call. The actual
 * work (LLM call + workspace write + cache refresh) lives in
 * {@link TurChatMemoryCompressionService#generateAndStore(CompressionJob)};
 * this class exists only because {@code @Async} requires the call to cross a
 * Spring proxy — invoking it via {@code this} inside the service would run
 * synchronously.
 *
 * <p>The service is injected {@code @Lazy} to break the
 * service → worker → service construction cycle.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurChatMemoryCompressionWorker {

    private final TurChatMemoryCompressionService compressionService;

    public TurChatMemoryCompressionWorker(@Lazy TurChatMemoryCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    /**
     * Regenerates the summary for one conversation off the request thread.
     * Exceptions are swallowed (logged) — a failed background summary just
     * leaves the conversation on its previous summary until the next attempt.
     */
    @Async(TurChatMemoryCompressionConfig.EXECUTOR_BEAN)
    public void regenerate(CompressionJob job) {
        try {
            compressionService.generateAndStore(job);
        } catch (RuntimeException e) {
            log.warn("[ChatMemoryCompression] async regeneration failed for conv '{}': {}",
                    job.conversationId(), e.getMessage());
        }
    }
}
